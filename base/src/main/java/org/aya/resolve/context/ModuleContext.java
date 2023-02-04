// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import org.aya.concrete.stmt.QualifiedID;
import org.aya.concrete.stmt.Stmt;
import org.aya.concrete.stmt.UseHide;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.ref.GenerateKind;
import org.aya.ref.LocalVar;
import org.aya.resolve.error.NameProblem;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * @author re-xyr
 */
public sealed interface ModuleContext extends Context permits NoExportContext, PhysicalModuleContext {
  @Override @NotNull Context parent();
  @Override default @NotNull Reporter reporter() {
    return parent().reporter();
  }
  @Override default @NotNull Path underlyingFile() {
    return parent().underlyingFile();
  }


  /**
   * All available symbols in this context<br>
   * {@code Unqualified -> (Module Name -> TopLevel)}<br>
   * It says an {@link AnyVar} can be referred by {@code {Module Name}::{Unqualified}}
   */
  @NotNull ModuleSymbol<AnyVar> symbols();

  /**
   * All imported modules in this context.<br/>
   * {@code Qualified Module -> Module Export}
   *
   * @apiNote empty list => this module
   * @implNote This module should be automatically imported.
   */
  @NotNull MutableMap<ModulePath.Qualified, ModuleExport> modules();


  /**
   * Things (symbol or module) that are exported by this module.
   */
  @NotNull ModuleExport exports();

  @Override default @Nullable ModuleExport getModuleLocalMaybe(@NotNull ModulePath.Qualified modName) {
    return modules().getOrNull(modName);
  }

  @Override default @Nullable AnyVar getUnqualifiedLocalMaybe(@NotNull String name, @NotNull SourcePos sourcePos) {
    var symbol = symbols().getUnqualifiedMaybe(name);
    if (symbol.isOk()) return symbol.get();

    // I am sure that this is not equivalent to null
    return switch (symbol.getErr()) {
      case NotFound -> null;
      case Ambiguous -> reportAndThrow(new NameProblem.AmbiguousNameError(
        name,
        ImmutableSeq.narrow(symbols().resolveUnqualified(name).keysView().toImmutableSeq()),
        sourcePos));
    };
  }

  @Override
  default @Nullable AnyVar getQualifiedLocalMaybe(@NotNull ModulePath.Qualified modName, @NotNull String name, @NotNull SourcePos sourcePos) {
    var mod = modules().getOrNull(modName);
    if (mod == null) return null;

    var ref = mod.symbols().getUnqualifiedMaybe(name);
    if (ref.isOk()) return ref.get();

    return switch (ref.getErr()) {
      case NotFound -> reportAndThrow(new NameProblem.QualifiedNameNotFoundError(modName, name, sourcePos));
      case Ambiguous -> reportAndThrow(new NameProblem.AmbiguousNameError(
        name,
        ImmutableSeq.narrow(mod.symbols().resolveUnqualified(name).keysView().toImmutableSeq()),
        sourcePos
      ));
    };
  }

  /**
   * Import the whole module (including itself and its re-exports)
   *
   * @see ModuleContext#importModule(ModulePath.Qualified, ModuleExport, Stmt.Accessibility, SourcePos)
   */
  default void importModule(
    @NotNull ModulePath.Qualified modName,
    @NotNull ModuleContext module,
    @NotNull Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos
  ) {
    var export = module.exports();
    importModule(modName, export, accessibility, sourcePos);
    export.modules().forEach((name, mod) -> importModule(modName.concat(name), mod, accessibility, sourcePos));
  }

  /**
   * Importing one module export.
   *
   * @param accessibility of importing, re-export if public
   * @param modName       the name of the module
   * @param moduleExport  the module
   */
  default void importModule(
    @NotNull ModulePath.Qualified modName,
    @NotNull ModuleExport moduleExport,
    @NotNull Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos
  ) {
    var modules = modules();
    if (modules.containsKey(modName)) {
      reportAndThrow(new NameProblem.DuplicateModNameError(modName, sourcePos));
    }
    if (getModuleMaybe(modName) != null) {
      reporter().report(new NameProblem.ModShadowingWarn(modName, sourcePos));
    }
    modules.set(modName, moduleExport);
  }

  /**
   * Open an imported module
   *
   * @param modName the name of the module
   * @param filter  use or hide which definitions
   * @param rename  renaming
   */
  default void openModule(
    @NotNull ModulePath.Qualified modName,
    @NotNull Stmt.Accessibility accessibility,
    @NotNull ImmutableSeq<QualifiedID> filter,
    @NotNull ImmutableSeq<WithPos<UseHide.Rename>> rename,
    @NotNull SourcePos sourcePos,
    UseHide.Strategy strategy
  ) {
    var modExport = getModuleMaybe(modName);
    if (modExport == null) reportAndThrow(new NameProblem.ModNameNotFoundError(modName, sourcePos));

    var filterRes = modExport.filter(filter, strategy);
    if (filterRes.anyError()) reportAllAndThrow(filterRes.problems(modName));

    var filtered = filterRes.result();
    var mapRes = filtered.map(rename);
    if (mapRes.anyError()) reportAllAndThrow(mapRes.problems(modName));

    // report all warning
    reportAll(filterRes.problems(modName).concat(mapRes.problems(modName)));

    var renamed = mapRes.result();
    renamed.symbols().forEach((name, candidates) -> candidates.forEach((componentName, ref) -> {
      var fullComponentName = modName.concat(componentName);
      addGlobal(true, ref, fullComponentName, name, accessibility, sourcePos);
    }));

    // import the modules that {renamed} exported
    renamed.modules().forEach((qname, mod) -> importModule(qname, mod, accessibility, sourcePos));
  }

  /**
   * Adding a new symbol to this module.
   */
  default void addGlobal(
    boolean imported,
    @NotNull AnyVar ref,
    @NotNull ModulePath modName,
    @NotNull String name,
    @NotNull Stmt.Accessibility acc,
    @NotNull SourcePos sourcePos
  ) {
    // `imported == false` implies the `ref` is defined in this module,
    // so `modName` should always be `ModulePath.This`.
    assert imported || modName == ModulePath.This : "Sanity check";

    var symbols = symbols();
    if (!symbols.contains(name)) {
      if (ref instanceof LocalVar localVar
        && getUnqualifiedMaybe(name, sourcePos) != null
        && !(localVar.generateKind() instanceof GenerateKind.Anonymous)) {
        // {name} isn't used in this scope, but used in outer scope, shadow!
        reporter().report(new NameProblem.ShadowingWarn(name, sourcePos));
      }
    } else if (symbols.contains(modName, name)) {
      reportAndThrow(new NameProblem.DuplicateNameError(name, ref, sourcePos));
    } else {
      reporter().report(new NameProblem.AmbiguousNameWarn(name, sourcePos));

      var candidates = symbols.resolveUnqualified(name);
      if (candidates.containsKey(ModulePath.This)) {
        // ignore importing
        return;
      } else if (modName == ModulePath.This) {
        // shadow
        candidates.clear();
      }
    }

    var result = symbols.add(modName, name, ref);
    assert result.isEmpty() : "Sanity check"; // should already be reported as an error

    // Only `DefVar`s can be exported.
    if (ref instanceof DefVar<?, ?> defVar && acc == Stmt.Accessibility.Public) {
      var success = exportSymbol(modName, name, defVar);
      if (!success) {
        reportAndThrow(new NameProblem.DuplicateExportError(name, sourcePos));
      }
    }
  }

  /**
   * Exporting an {@link AnyVar} with qualified id {@code {modName}::{name}}
   *
   * @return true if exported successfully, otherwise (when there already exist a symbol with the same name) false.
   */
  default boolean exportSymbol(@NotNull ModulePath modName, @NotNull String name, @NotNull DefVar<?, ?> ref) {
    return true;
  }

  default void defineSymbol(@NotNull AnyVar ref, @NotNull Stmt.Accessibility accessibility, @NotNull SourcePos sourcePos) {
    addGlobal(false, ref, ModulePath.This, ref.name(), accessibility, sourcePos);
  }
}
