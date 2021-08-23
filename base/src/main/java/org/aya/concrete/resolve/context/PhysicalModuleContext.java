// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.context;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.concrete.resolve.error.DuplicateExportError;
import org.aya.concrete.stmt.Stmt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author re-xyr
 */
public final class PhysicalModuleContext implements ModuleContext {
  public final @NotNull Context parent;
  public final @NotNull MutableMap<String, MutableMap<Seq<String>, Var>> definitions = MutableHashMap.of();
  public final @NotNull MutableMap<ImmutableSeq<String>, MutableMap<String, Var>> modules = MutableHashMap.of(TOP_LEVEL_MOD_NAME, MutableHashMap.of());
  public final @NotNull MutableMap<ImmutableSeq<String>, MutableMap<String, Var>> exports = MutableHashMap.of(TOP_LEVEL_MOD_NAME, MutableHashMap.of());

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
    MutableMap<String, Var> mod
  ) {
    ModuleContext.super.importModule(accessibility, sourcePos, componentName, mod);
    if (accessibility == Stmt.Accessibility.Public) exports.set(componentName, mod);
  }

  @Override public void addGlobal(
    @NotNull ImmutableSeq<String> modName,
    @NotNull String name,
    @NotNull Stmt.Accessibility accessibility,
    @NotNull Var ref,
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

  @Override public @NotNull MutableMap<String, MutableMap<Seq<String>, Var>> definitions() {
    return definitions;
  }

  @Override public @NotNull MutableMap<ImmutableSeq<String>, MutableMap<String, Var>> modules() {
    return modules;
  }
}
