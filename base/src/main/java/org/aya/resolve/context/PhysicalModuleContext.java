// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import org.aya.concrete.stmt.Stmt;
import org.aya.ref.AnyVar;
import org.aya.resolve.error.DuplicateExportError;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author re-xyr
 */
public non-sealed class PhysicalModuleContext implements ModuleContext {
  public final @NotNull Context parent;
  public final @NotNull MutableMap<String, MutableMap<Seq<String>, AnyVar>> definitions = MutableHashMap.create();
  public final @NotNull MutableMap<ImmutableSeq<String>, MutableMap<String, AnyVar>> modules = MutableHashMap.of(TOP_LEVEL_MOD_NAME, MutableHashMap.create());
  public final @NotNull MutableMap<ImmutableSeq<String>, MutableMap<String, AnyVar>> exports = MutableHashMap.of(TOP_LEVEL_MOD_NAME, MutableHashMap.create());

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
    @NotNull Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos,
    ImmutableSeq<String> componentName,
    MutableMap<String, AnyVar> mod
  ) {
    ModuleContext.super.importModule(accessibility, sourcePos, componentName, mod);
    if (accessibility == Stmt.Accessibility.Public) exports.set(componentName, mod);
  }

  @Override public void addGlobal(
    @NotNull ImmutableSeq<String> modName,
    @NotNull String name,
    @NotNull Stmt.Accessibility accessibility,
    @NotNull AnyVar ref,
    @NotNull SourcePos sourcePos
  ) {
    ModuleContext.super.addGlobal(modName, name, accessibility, ref, sourcePos);
    if (accessibility == Stmt.Accessibility.Public) {
      if (exports.get(TOP_LEVEL_MOD_NAME).containsKey(name)) {
        reportAndThrow(new DuplicateExportError(name, sourcePos));
      } else exports.get(TOP_LEVEL_MOD_NAME).set(name, ref);
    }
  }

  public @NotNull NoExportContext exampleContext() {
    if (exampleContext == null) exampleContext = new NoExportContext(this);
    return exampleContext;
  }

  @Override public @NotNull Context parent() {
    return parent;
  }

  @Override public @NotNull MutableMap<String, MutableMap<Seq<String>, AnyVar>> definitions() {
    return definitions;
  }

  @Override public @NotNull MutableMap<ImmutableSeq<String>, MutableMap<String, AnyVar>> modules() {
    return modules;
  }
}
