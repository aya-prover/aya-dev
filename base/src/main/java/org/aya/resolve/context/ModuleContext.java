// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.value.primitive.MutableBooleanValue;
import org.aya.resolve.error.NameProblem;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.concrete.stmt.QualifiedID;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.concrete.stmt.UseHide;
import org.aya.syntax.context.Candidate;
import org.aya.syntax.context.ModuleContextView;
import org.aya.syntax.context.ModuleExport;
import org.aya.syntax.ref.AnyDefVar;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.GenerateKind;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/// A Context for Module.
/// A module may import symbols/modules and export some symbols/modules, it also defines some symbols/modules.
/// However, name conflicting is a problem during using module, in order to solve it easier in both
/// designer side and user side, a module should hold these properties:
///
/// - No ambiguity on module name: module name conflicting is hard to solve,
///   unless we introduce unique qualified name for each module which is a little complicate.
///   Also, there are some implementation problems.
/// - No ambiguity on exported symbol name: ambiguous on symbol name is acceptable, as long as it won't be exported.
///
/// We also don't handle the case that we have `b::c` in `a` and `c` in `a::b` simultaneously.
///
/// @author re-xyr
public interface ModuleContext extends ModuleContextView, Context {
  @Override @NotNull Context parent();
  @Override default @NotNull Path underlyingFile() { return parent().underlyingFile(); }

  /// Things (symbol or module) that are exported by this module.
  @NotNull ModuleExport exports();

  @Override default @Nullable ModuleExport getModuleLocalMaybe(@NotNull ModuleName.Qualified modName) {
    return modules().getOrNull(modName);
  }

  @Override
  default @Nullable Candidate<AnyVar> getCandidateLocalMaybe(@NotNull String name, @NotNull SourcePos sourcePos) {
    return symbols().getNonEmpty(name);
  }

  @Override default @Nullable Option<AnyVar>
  getQualifiedLocalMaybe(ModuleName.@NotNull Qualified modName, @NotNull String name, @NotNull SourcePos sourcePos, @NotNull Reporter reporter) {
    var mod = getModuleLocalMaybe(modName);
    if (mod == null) return null;
    var symbol = mod.symbols().getOrNull(name);
    if (symbol == null) {
      reporter.report(new NameProblem.QualifiedNameNotFoundError(modName, name, sourcePos));
      return Option.none();
    }
    return Option.some(symbol);
  }

  /// Import modules from {@param module}, this method also import modules
  /// that inside {@param module}.
  ///
  /// @see ModuleContext#importModule
  default boolean importModuleContext(
    @NotNull ModuleName.Qualified modName,
    @NotNull ModuleContext module,
    @NotNull Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos,
    @NotNull Reporter reporter
  ) {
    var success = MutableBooleanValue.create(true);
    var export = module.exports();
    success.set(importModule(modName, export, accessibility, sourcePos, reporter));
    export.modules().forEachChecked((qname, innerMod) -> {
      var result = importModule(modName.concat(qname), innerMod, accessibility, sourcePos, reporter);
      success.getAndUpdate(t -> t && result);
    });

    return success.get();
  }

  /**
   * Importing one module export.
   *
   * @param accessibility of importing, re-export if public
   * @param modName       the name of the module
   * @param moduleExport  the module
   */
  default boolean importModule(
    @NotNull ModuleName.Qualified modName,
    @NotNull ModuleExport moduleExport,
    @NotNull Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos,
    @NotNull Reporter reporter
  ) {
    var exists = modules().getOrNull(modName);
    if (exists != null) {
      if (exists == moduleExport) return true;
      reporter.report(new NameProblem.DuplicateModNameError(modName, sourcePos));
      return false;
    } else if (getModuleMaybe(modName) != null) {
      reporter.report(new NameProblem.ModShadowingWarn(modName, sourcePos));
      return true;
    }

    // put after check, otherwise you will get a lot of ModShadowingWarn!
    modules().put(modName, moduleExport);
    return true;
  }

  default boolean openModule(
    @NotNull ModuleName.Qualified modName,
    @NotNull Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos,
    @NotNull UseHide useHide,
    @NotNull Reporter reporter
  ) {
    return openModule(modName, accessibility,
      useHide.list().map(UseHide.Name::id),
      useHide.renaming(),
      sourcePos, useHide.strategy(), reporter);
  }

  /**
   * Open an imported module
   *
   * @param modName the name of the module
   * @param filter  use or hide which definitions
   * @param rename  renaming
   */
  default boolean openModule(
    @NotNull ModuleName.Qualified modName,
    @NotNull Stmt.Accessibility accessibility,
    @NotNull ImmutableSeq<QualifiedID> filter,
    @NotNull ImmutableSeq<WithPos<UseHide.Rename>> rename,
    @NotNull SourcePos sourcePos,
    @NotNull UseHide.Strategy strategy,
    @NotNull Reporter reporter
  ) {
    var modExport = getModuleMaybe(modName);
    if (modExport == null) {
      reporter.report(new NameProblem.ModNameNotFoundError(modName, sourcePos));
      return false;
    }

    var filterRes = modExport.filter(filter, strategy);
    var filterProblem = collectProblems(filterRes, modName);
    if (filterRes.anyError()) {
      reporter.reportAll(filterProblem);
      return false;
    }

    var mapRes = filterRes.result().map(rename);
    var mapProblem = collectProblems(mapRes, modName);
    if (mapRes.anyError()) {
      reporter.reportAll(mapProblem);
      return false;
    }

    // report all warnings
    reporter.reportAll(filterProblem.concat(mapProblem));

    var renamed = mapRes.result();
    renamed.symbols().forEach((name, ref) ->
      importSymbol(ref, modName, name, accessibility, sourcePos, reporter));

    // import the modules that {renamed} exported
    renamed.modules().forEach((qname, mod) ->
      importModule(qname, mod, accessibility, sourcePos, reporter));

    return true;
  }

  /// Adding a new symbol to this module.
  default boolean importSymbol(
    @NotNull AnyVar ref,
    @NotNull ModuleName fromModule,
    @NotNull String name,
    @NotNull Stmt.Accessibility acc,
    @NotNull SourcePos sourcePos,
    @NotNull Reporter reporter
  ) {
    var symbols = symbols();
    var candidates = symbols.getNonEmpty(name);
    if (candidates == null) {
      var candy = getCandidateMaybe(name, sourcePos);
      if (candy != null && (!(ref instanceof LocalVar local) || local.generateKind() != GenerateKind.Basic.Anonymous)) {
        // {name} isn't used in this scope, but used in outer scope, shadow!
        reporter.report(new NameProblem.ShadowingWarn(name, sourcePos));
      }
    } else if (candidates.from().contains(fromModule)) {
      // this case happens when the user is trying to open a module in twice (even the symbol are equal)
      // or define two symbols with same name ([fromModule == ModuleName.This])
      reporter.report(new NameProblem.DuplicateNameError(name, ref, sourcePos));
      return false;
    } else if (candidates.isAmbiguous() || candidates.get() != ref) {
      reporter.report(new NameProblem.AmbiguousNameWarn(name, sourcePos));
    }

    symbols.add(name, ref, fromModule);

    // Only `AnyDefVar`s can be exported.
    if (ref instanceof AnyDefVar defVar && acc == Stmt.Accessibility.Public) {
      var success = exportSymbol(name, defVar);
      if (!success) {
        reporter.report(new NameProblem.DuplicateExportError(name, sourcePos));
      }
    }

    return true;
  }

  /**
   * Exporting an {@link AnyDefVar}.
   *
   * @return true if exported successfully, otherwise (when there already exist a symbol with the same name) false.
   */
  default boolean exportSymbol(@NotNull String name, @NotNull AnyDefVar ref) { return true; }

  default boolean defineSymbol(
    @NotNull AnyVar ref, @NotNull Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos, @NotNull Reporter reporter
  ) {
    return importSymbol(ref, ModuleName.This, ref.name(), accessibility, sourcePos, reporter);
  }

  static SeqView<Problem> collectProblems(@NotNull ModuleExport.ExportResult result, @NotNull ModuleName modName) {
    SeqView<Problem> invalidNameProblems = result.invalidNames().view()
      .map(name -> new NameProblem.QualifiedNameNotFoundError(
        modName.concat(name.component()),
        name.name(),
        name.sourcePos()));

    SeqView<Problem> shadowNameProblems = result.shadowNames().view()
      .map(name -> new NameProblem.ShadowingWarn(name.data(), name.sourcePos()));

    return shadowNameProblems.concat(invalidNameProblems);
  }
}
