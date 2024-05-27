// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import org.aya.resolve.error.NameProblem;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.concrete.stmt.QualifiedID;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.concrete.stmt.UseHide;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.ref.GenerateKind;
import org.aya.syntax.ref.LocalVar;
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

  @Override default @NotNull Reporter reporter() { return parent().reporter(); }
  @Override default @NotNull Path underlyingFile() { return parent().underlyingFile(); }

  /**
   * All available symbols in this context
   */
  @NotNull ModuleSymbol<AnyVar> symbols();

  /**
   * All imported modules in this context.<br/>
   * {@code Qualified Module -> Module Export}
   *
   * @apiNote empty list => this module
   * @implNote This module should be automatically imported.
   */
  @NotNull MutableMap<ModuleName.Qualified, ModuleExport> modules();

  /**
   * Things (symbol or module) that are exported by this module.
   */
  @NotNull ModuleExport exports();

  @Override default @Nullable ModuleExport getModuleLocalMaybe(@NotNull ModuleName.Qualified modName) {
    return modules().getOrNull(modName);
  }

  @Override default @Nullable AnyVar getUnqualifiedLocalMaybe(@NotNull String name, @NotNull SourcePos sourcePos) {
    var symbol = symbols().getUnqualifiedMaybe(name);
    if (symbol.isOk()) return symbol.get();
    switch (symbol.getErr()) {
      case NotFound -> { }
      case Ambiguous -> reportAndThrow(new NameProblem.AmbiguousNameError(
        name, symbols().resolveUnqualified(name).moduleNames(), sourcePos));
    }
    return null;
  }

  @Override
  default @Nullable AnyVar getQualifiedLocalMaybe(@NotNull ModuleName.Qualified modName, @NotNull String name, @NotNull SourcePos sourcePos) {
    var mod = modules().getOrNull(modName);
    if (mod == null) return null;

    var ref = mod.symbols().getUnqualifiedMaybe(name);
    if (ref.isOk()) return ref.get();

    return switch (ref.getErr()) {
      case NotFound -> reportAndThrow(new NameProblem.QualifiedNameNotFoundError(modName, name, sourcePos));
      case Ambiguous -> reportAndThrow(new NameProblem.AmbiguousNameError(
        name,
        mod.symbols().resolveUnqualified(name).moduleNames(),
        sourcePos
      ));
    };
  }

  /**
   * Import the whole module (including itself and its re-exports)
   *
   * @see ModuleContext#importModule(ModuleName.Qualified, ModuleExport, Stmt.Accessibility, SourcePos)
   */
  default void importModule(
    @NotNull ModuleName.Qualified modName,
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
    @NotNull ModuleName.Qualified modName,
    @NotNull ModuleExport moduleExport,
    @NotNull Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos
  ) {
    var modules = modules();
    var exists = modules.getOrNull(modName);
    if (exists != null) {
      if (exists != moduleExport) {
        reportAndThrow(new NameProblem.DuplicateModNameError(modName, sourcePos));
      } else return;
    } else if (getModuleMaybe(modName) != null) {
      reporter().report(new NameProblem.ModShadowingWarn(modName, sourcePos));
    }

    modules.set(modName, moduleExport);
  }
  default void openModule(
    @NotNull ModuleName.Qualified modName,
    @NotNull Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos,
    @NotNull UseHide useHide
  ) {
    openModule(modName, accessibility,
      useHide.list().map(UseHide.Name::id),
      useHide.renaming(),
      sourcePos, useHide.strategy());
  }

  /**
   * Open an imported module
   *
   * @param modName the name of the module
   * @param filter  use or hide which definitions
   * @param rename  renaming
   */
  default void openModule(
    @NotNull ModuleName.Qualified modName,
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

    // report all warnings
    reportAll(filterRes.problems(modName).concat(mapRes.problems(modName)));

    var renamed = mapRes.result();
    renamed.symbols().forEach((name, candidates) -> candidates.forEach((componentName, ref) -> {
      // TODO: {componentName} can be invisible, so {fullComponentName} is probably incorrect
      var fullComponentName = modName.concat(componentName);
      importSymbol(true, ref, fullComponentName, name, accessibility, sourcePos);
    }));

    // import the modules that {renamed} exported
    renamed.modules().forEach((qname, mod) -> importModule(qname, mod, accessibility, sourcePos));
  }

  /**
   * Adding a new symbol to this module.
   */
  default void importSymbol(
    boolean imported,
    @NotNull AnyVar ref,
    @NotNull ModuleName modName,
    @NotNull String name,
    @NotNull Stmt.Accessibility acc,
    @NotNull SourcePos sourcePos
  ) {
    // `imported == false` implies the `ref` is defined in this module,
    // so `modName` should always be `ModulePath.This`.
    assert imported || modName == ModuleName.This : "Sanity check";

    var symbols = symbols();
    var candidates = symbols.resolveUnqualified(name);
    if (candidates.map().isEmpty()) {
      if (getUnqualifiedMaybe(name, sourcePos) != null
        && (!(ref instanceof LocalVar local) || local.generateKind() != GenerateKind.Basic.Anonymous)) {
        // {name} isn't used in this scope, but used in outer scope, shadow!
        reporter().report(new NameProblem.ShadowingWarn(name, sourcePos));
      }
    } else if (candidates.map().containsKey(modName)) {
      reportAndThrow(new NameProblem.DuplicateNameError(name, ref, sourcePos));
    } else {
      var uniqueCandidates = candidates.uniqueCandidates();
      if (uniqueCandidates.size() != 1 || uniqueCandidates.iterator().next() != ref) {
        reporter().report(new NameProblem.AmbiguousNameWarn(name, sourcePos));

        if (candidates.map().containsKey(ModuleName.This)) {
          // H : modName instance ModulePath.Qualified
          // H0 : ref !in uniqueCandidates
          assert candidates.map().size() == 1;
          // ignore importing
          return;
        } else if (modName == ModuleName.This) {
          // H : candidates.keys are all Qualified
          // shadow
          candidates.asMut().get().clear();
        }
      } else {
        // H : uniqueCandidates.size == 1 && uniqueCandidates.iterator().next() == ref
        assert modName != ModuleName.This : "Sanity check";     // already reported
        assert candidates.moduleNames().allMatch(x -> x instanceof ModuleName.Qualified);
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
  default boolean exportSymbol(@NotNull ModuleName modName, @NotNull String name, @NotNull DefVar<?, ?> ref) {
    return true;
  }

  default void defineSymbol(@NotNull AnyVar ref, @NotNull Stmt.Accessibility accessibility, @NotNull SourcePos sourcePos) {
    importSymbol(false, ref, ModuleName.This, ref.name(), accessibility, sourcePos);
  }
}
