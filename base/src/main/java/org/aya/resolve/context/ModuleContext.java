// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import org.aya.resolve.error.NameProblem;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.concrete.stmt.QualifiedID;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.concrete.stmt.UseHide;
import org.aya.syntax.ref.AnyDefVar;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.GenerateKind;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
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
 *     No ambiguity on module name: module name conflicting is hard to solve,
 *     unless we introduce unique qualified name for each module which is a little complicate.
 *     Also, there are some implementation problems.
 *   </li>
 *   <li>
 *     No ambiguity on exported symbol name: ambiguous on symbol name is acceptable, as long as it won't be exported.
 *   </li>
 * </ol>
 * <br/>
 * We also don't handle the case that we have {@code b::c} in {@code a} and {@code c} in {@code a::b} simultaneously.
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
  @NotNull ModuleSymbol<AnyVar> symbols();

  /**
   * All imported modules in this context.<br/>
   * {@code Qualified Module -> Module Export}
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
    var symbol = symbols().get(name);
    if (symbol.isEmpty()) return null;
    if (symbol.isAmbiguous()) reportAndThrow(new NameProblem.AmbiguousNameError(
      name, symbol.from(), sourcePos));

    return symbol.get();
  }

  @Override
  default @Nullable AnyVar getQualifiedLocalMaybe(@NotNull ModuleName.Qualified modName, @NotNull String name, @NotNull SourcePos sourcePos) {
    var mod = getModuleLocalMaybe(modName);
    if (mod == null) return null;
    var symbol = mod.symbols().getOrNull(name);
    if (symbol == null) {
      reportAndThrow(new NameProblem.QualifiedNameNotFoundError(modName, name, sourcePos));
    }
    return symbol;
  }

  /**
   * Import modules from {@param module}, this method also import modules
   * that inside {@param module}.
   *
   * @see ModuleContext#importModule(ModuleName.Qualified, ModuleExport, Stmt.Accessibility, SourcePos)
   */
  default void importModuleContext(
    @NotNull ModuleName.Qualified modName,
    @NotNull ModuleContext module,
    @NotNull Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos
  ) {
    var export = module.exports();
    importModule(modName, export, accessibility, sourcePos);
    export.modules().forEach((qname, innerMod) ->
      importModule(modName.concat(qname), innerMod, accessibility, sourcePos));
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
    var exists = modules().getOrNull(modName);
    if (exists != null) {
      if (exists == moduleExport) return;
      reportAndThrow(new NameProblem.DuplicateModNameError(modName, sourcePos));
    } else if (getModuleMaybe(modName) != null) {
      fail(new NameProblem.ModShadowingWarn(modName, sourcePos));
    }

    // put after check, otherwise you will get a lot of ModShadowingWarn!
    modules().put(modName, moduleExport);
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
    if (modExport == null)
      reportAndThrow(new NameProblem.ModNameNotFoundError(modName, sourcePos));

    var filterRes = modExport.filter(filter, strategy);
    var filterProblem = filterRes.problems(modName);
    if (filterRes.anyError()) reportAllAndThrow(filterProblem);

    var mapRes = filterRes.result().map(rename);
    var mapProblem = mapRes.problems(modName);
    if (mapRes.anyError()) reportAllAndThrow(mapProblem);

    // report all warnings
    reportAll(filterProblem.concat(mapProblem));

    var renamed = mapRes.result();
    renamed.symbols().forEach((name, ref) ->
      importSymbol(ref, modName, name, accessibility, sourcePos));

    // import the modules that {renamed} exported
    renamed.modules().forEach((qname, mod) -> importModule(qname, mod, accessibility, sourcePos));
  }

  /**
   * Adding a new symbol to this module.
   */
  default void importSymbol(
    @NotNull AnyVar ref,
    @NotNull ModuleName fromModule,
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
    } else if (candidates.from().contains(fromModule)) {
      // this case happens when the user is trying to open a module in twice (even the symbol are equal)
      // or define two symbols with same name ([fromModule == ModuleName.This])
      reportAndThrow(new NameProblem.DuplicateNameError(name, ref, sourcePos));
    } else if (candidates.isAmbiguous() || candidates.get() != ref) {
      fail(new NameProblem.AmbiguousNameWarn(name, sourcePos));
    }

    symbols.add(name, ref, fromModule);

    // Only `AnyDefVar`s can be exported.
    if (ref instanceof AnyDefVar defVar && acc == Stmt.Accessibility.Public) {
      var success = exportSymbol(name, defVar);
      if (!success) {
        reportAndThrow(new NameProblem.DuplicateExportError(name, sourcePos));
      }
    }
  }

  /**
   * Exporting an {@link AnyDefVar}.
   *
   * @return true if exported successfully, otherwise (when there already exist a symbol with the same name) false.
   */
  default boolean exportSymbol(@NotNull String name, @NotNull AnyDefVar ref) { return true; }

  default void defineSymbol(@NotNull AnyVar ref, @NotNull Stmt.Accessibility accessibility, @NotNull SourcePos sourcePos) {
    importSymbol(ref, ModuleName.This, ref.name(), accessibility, sourcePos);
  }
}
