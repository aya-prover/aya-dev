// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import org.aya.resolve.error.NameProblem;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.concrete.stmt.QualifiedID;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.concrete.stmt.UseHide;
import org.aya.syntax.ref.*;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * A Context for Module.<br/>
 * A module may import symbols/modules and export some symbols/modules, it also defines some symbols/modules.
 * However, name conflicting is a problem during using module, in order to solve it easier in both
 * designer side and user side, a module should hold these properties:
 * <ol>
 *   <li>
 *     No ambiguous on module name: module name conflicting is hard to solve,
 *     unless we introduce unique qualified name for each module. There are also some implementation problems.
 *   </li>
 *   <li>
 *    No ambiguous on exported symbol name: ambiguous on symbol name is acceptable, as long as it won't be exported.
 *   </li>
 * </ol>
 *
 * @author re-xyr
 */
public sealed interface ModuleContext extends Context permits NoExportContext, PhysicalModuleContext {
  @Override @NotNull Context parent();

  @Override default @NotNull Reporter reporter() { return parent().reporter(); }
  @Override default @NotNull Path underlyingFile() { return parent().underlyingFile(); }

  /**
   * All available symbols in this context
   */
  @NotNull ModuleSymbol2<AnyVar> symbols();

  /**
   * All imported modules in this context.<br/>
   * {@code Module Name -> Module Export}
   */
  @NotNull MutableMap<String, ModuleExport2> modules();

  /**
   * Things (symbol or module) that are exported by this module.
   */
  @NotNull ModuleExport2 exports();

  @Override default @Nullable ModuleExport2 getModuleLocalMaybe(@NotNull ModuleName.Qualified modName) {
    var head = modName.head();
    var tail = modName.tail();
    var mod = modules().getOrNull(head);
    if (mod == null) return null;
    if (tail == null) return mod;
    return mod.resolveModule(tail);
  }

  @Override default @Nullable AnyVar getUnqualifiedLocalMaybe(@NotNull String name, @NotNull SourcePos sourcePos) {
    var symbol = symbols().get(name);
    if (symbol.isEmpty()) return null;
    if (symbol.isAmbiguous()) reportAndThrow(new NameProblem.AmbiguousNameError(
      name, ImmutableSeq.from(symbol.from()
      .map(x -> x.resolve(name))),
      sourcePos
    ));

    return symbol.get();
  }

  @Override
  default @Nullable AnyVar getQualifiedLocalMaybe(@NotNull ModuleName.Qualified modName, @NotNull String name, @NotNull SourcePos sourcePos) {
    var mod = getModuleLocalMaybe(modName);
    if (mod == null) return null;
    return mod.symbols().getOrNull(name);
  }

  /**
   * @see ModuleContext#importModule(String, ModuleExport2, Stmt.Accessibility, boolean, SourcePos)
   */
  default void importModule(
    @NotNull String modName,
    @NotNull ModuleContext module,
    @NotNull Stmt.Accessibility accessibility,
    boolean isDefined,
    @NotNull SourcePos sourcePos
  ) {
    var export = module.exports();
    importModule(modName, export, accessibility, isDefined, sourcePos);
  }

  /**
   * Importing one module export.
   *
   * @param accessibility of importing, re-export if public
   * @param modName       the name of the module
   * @param moduleExport  the module--[=]
   */
  default void importModule(
    @NotNull String modName,
    @NotNull ModuleExport2 moduleExport,
    @NotNull Stmt.Accessibility accessibility,
    boolean isDefined,
    @NotNull SourcePos sourcePos
  ) {
    var modules = modules();
    var qname = new ModuleName.Qualified(modName);
    var exists = modules.getOrNull(modName);
    if (exists != null && !isDefined) {
      if (exists != moduleExport) {
        reportAndThrow(new NameProblem.DuplicateModNameError(qname, sourcePos));
      } else return;
    } else if (getModuleMaybe(qname) != null) {
      fail(new NameProblem.ModShadowingWarn(qname, sourcePos));
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
      useHide.list().map(UseHide.Name::name),
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
    @NotNull ImmutableSeq<WithPos<String>> filter,
    @NotNull ImmutableSeq<WithPos<UseHide.Rename>> rename,
    @NotNull SourcePos sourcePos,
    UseHide.Strategy strategy
  ) {
    var modExport = getModuleMaybe(modName);
    if (modExport == null)
      reportAndThrow(new NameProblem.ModNameNotFoundError(modName, sourcePos));

    var filterRes = modExport.filter(filter, strategy);
    if (filterRes.anyError()) reportAllAndThrow(filterRes.problems());

    var filtered = filterRes.result();
    var mapRes = filtered.map(rename);
    if (mapRes.anyError()) reportAllAndThrow(mapRes.problems());

    // report all warnings
    reportAll(filterRes.problems().concat(mapRes.problems()));

    var renamed = mapRes.result();
    renamed.symbols().forEach((name, ref) -> {
      importSymbol(ref, modName, name, accessibility, sourcePos);
    });

    // import the modules that {renamed} exported
    renamed.modules().forEach((qname, mod) -> importModule(qname, mod, accessibility, false, sourcePos));
  }

  /**
   * Adding a new symbol to this module.
   */
  default void importSymbol(
    @NotNull AnyVar ref,
    @NotNull ModuleName modName,
    @NotNull String name,
    @NotNull Stmt.Accessibility acc,
    @NotNull SourcePos sourcePos
  ) {
    var symbols = symbols();
    var candidates = symbols.get(name);
    if (candidates.isEmpty()) {
      if (getUnqualifiedMaybe(name, sourcePos) != null
        && (!(ref instanceof LocalVar local) || local.generateKind() != GenerateKind.Basic.Anonymous)) {
        // {name} isn't used in this scope, but used in outer scope, shadow!
        fail(new NameProblem.ShadowingWarn(name, sourcePos));
      }
    } else if (candidates.from().contains(modName)) {
      reportAndThrow(new NameProblem.DuplicateNameError(name, ref, sourcePos));
    } else {
      if (candidates.isAmbiguous()) {
        fail(new NameProblem.AmbiguousNameWarn(name, sourcePos));
        // symbols.add(name, ref, modName);
      }
    }

    symbols.add(name, ref, modName);

    // Only `AnyDefVar`s can be exported.
    if (ref instanceof AnyDefVar defVar && acc == Stmt.Accessibility.Public) {
      var success = exportSymbol(name, defVar);
      if (!success) {
        reportAndThrow(new NameProblem.DuplicateExportError(name, sourcePos));    // TODO: use a more appropriate error
      }
    }
  }

  /**
   * Exporting an {@link AnyVar} with qualified id {@code {modName}::{name}}
   *
   * @return true if exported successfully, otherwise (when there already exist a symbol with the same name) false.
   */
  default boolean exportSymbol(@NotNull String name, @NotNull AnyDefVar ref) {
    return true;
  }

  default void defineSymbol(@NotNull AnyVar ref, @NotNull Stmt.Accessibility accessibility, @NotNull SourcePos sourcePos) {
    importSymbol(ref, ModuleName.This, ref.name(), accessibility, sourcePos);
  }
}
