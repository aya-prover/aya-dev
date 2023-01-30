// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import org.aya.concrete.stmt.Stmt;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.resolve.error.NameProblem;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author re-xyr
 */
public non-sealed class PhysicalModuleContext implements ModuleContext {
  public final @NotNull Context parent;
  public final @NotNull ModuleSymbol<AnyVar> symbols = new ModuleSymbol<>();
  public final @NotNull ModuleExport thisModuleExport = new ModuleExport();
  public final @NotNull MutableMap<ModulePath.Qualified, ModuleExport> modules = MutableHashMap.create();
  public final @NotNull MutableMap<ModulePath, ModuleExport> exports =
    MutableHashMap.of(ModulePath.This, thisModuleExport);
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

  @Override public void importModuleExport(
    @NotNull ModulePath.Qualified componentName,
    @NotNull ModuleExport modExport,
    @NotNull Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos
  ) {
    ModuleContext.super.importModuleExport(componentName, modExport, accessibility, sourcePos);
    if (accessibility == Stmt.Accessibility.Public) {
      this.exports.set(componentName, modExport);
    }
  }

  @Override
  public void doExport(@NotNull ModulePath componentName, @NotNull String name, @NotNull DefVar<?, ?> ref, @NotNull SourcePos sourcePos) {
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

  @Override public @NotNull ModuleSymbol<AnyVar> symbols() {
    return symbols;
  }

  @Override public @NotNull MutableMap<ModulePath.Qualified, ModuleExport> modules() {
    return modules;
  }

  @Override public @NotNull MutableMap<ModulePath, ModuleExport> exports() {
    return exports;
  }
}
