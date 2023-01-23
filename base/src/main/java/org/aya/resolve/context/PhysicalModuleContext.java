// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.Map;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableSet;
import org.aya.concrete.stmt.Stmt;
import org.aya.ref.DefVar;
import org.aya.resolve.error.NameProblem;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author re-xyr
 */
public non-sealed class PhysicalModuleContext implements ModuleContext {
  public final @NotNull Context parent;
  public final @NotNull MutableModuleSymbol<ContextUnit.TopLevel> symbols = new MutableModuleSymbol<>();
  public final @NotNull MutableModuleExport thisModule = new MutableModuleExport();
  public final @NotNull MutableModuleExport thisModuleExport = new MutableModuleExport();
  public final @NotNull MutableMap<ModulePath, ModuleExport> modules =
          MutableHashMap.of(ModulePath.This, thisModule);
  public final @NotNull MutableMap<ModulePath, ModuleExport> exports =
          MutableHashMap.of(ModulePath.This, thisModuleExport);
  public final @NotNull MutableSet<String> duplicated = MutableSet.create();

  private final @NotNull ImmutableSeq<String> moduleName;

  @Override
  public @NotNull ImmutableSeq<String> moduleName() {
    return moduleName;
  }

  private @Nullable NoExportContext exampleContext;

  public PhysicalModuleContext(@NotNull Context parent, @NotNull ImmutableSeq<String> moduleName) {
    this.parent = parent;
    this.moduleName = moduleName;
  }

  @Override public void importModule(
          @NotNull ModulePath.Qualified componentName,
          @NotNull ModuleExport modExport,
          @NotNull Stmt.Accessibility accessibility,
          @NotNull SourcePos sourcePos
  ) {
    ModuleContext.super.importModule(componentName, modExport, accessibility, sourcePos);
    if (accessibility == Stmt.Accessibility.Public) {
      this.exports.set(componentName, modExport);
    }
  }

  @Override
  public void doExport(@NotNull ModulePath componentName, @NotNull String name, @NotNull DefVar<?, ?> ref, @NotNull SourcePos sourcePos) {
    if (duplicated.contains(name)) return;

    var success = thisModuleExport.export(componentName, name, ref);

    if (!success) {
      reportAndThrow(new NameProblem.DuplicateExportError(name, sourcePos));
    }
  }

  public @NotNull NoExportContext exampleContext() {
    if (exampleContext == null) exampleContext = new NoExportContext(this);
    return exampleContext;
  }

  @Override public @NotNull Context parent() {
    return parent;
  }

  @Override public @NotNull MutableModuleSymbol<ContextUnit.TopLevel> symbols() {
    return symbols;
  }

  @Override public @NotNull MutableMap<ModulePath, ModuleExport> modules() {
    return modules;
  }

  @Override public @NotNull Map<ModulePath, ModuleExport> exports() {
    return Map.from(exports);
  }

  @Override public @NotNull MutableModuleExport thisModule() {
    return thisModule;
  }

  @Contract(mutates = "this") public void clear() {
    modules.clear();
    exports.clear();
    symbols.clear();
  }
}
