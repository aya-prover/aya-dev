// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.value.MutableValue;
import org.aya.cli.utils.RepoLike;
import org.aya.concrete.stmt.Stmt;
import org.aya.ref.AnyVar;
import org.aya.resolve.context.Context;
import org.aya.resolve.context.ModuleExport;
import org.aya.resolve.context.PhysicalModuleContext;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ReplContext extends PhysicalModuleContext implements RepoLike<ReplContext> {
  private final @NotNull MutableValue<@Nullable ReplContext> downstream = MutableValue.create();

  public ReplContext(@NotNull Context parent, @NotNull ImmutableSeq<String> name) {
    super(parent, name);
  }

  @Override
  public void addGlobal(
    @NotNull ImmutableSeq<String> modName,
    @NotNull String name,
    Stmt.@NotNull Accessibility accessibility,
    @NotNull AnyVar ref,
    @NotNull SourcePos sourcePos) {
    definitions.getOrPut(name, MutableHashMap::of).set(modName, ref);
    if (accessibility == Stmt.Accessibility.Public) {
      exports.get(TOP_LEVEL_MOD_NAME).exportAnyway(name, ref);
    }
  }

  @Override
  public void importModule(
    Stmt.@NotNull Accessibility accessibility,
    @NotNull SourcePos sourcePos,
    ImmutableSeq<String> componentName,
    ModuleExport mod) {
    modules.put(componentName, mod);
    if (accessibility == Stmt.Accessibility.Public) exports.set(componentName, mod);
  }

  @Override
  public @NotNull ReplContext derive(@NotNull Seq<@NotNull String> extraName) {
    return new ReplContext(this, this.moduleName().concat(extraName));
  }

  @Override
  public @NotNull ReplContext derive(@NotNull String extraName) {
    return new ReplContext(this, this.moduleName().appended(extraName));
  }

  @Override public @NotNull MutableValue<ReplContext> downstream() {
    return downstream;
  }

  public @NotNull ReplContext fork() {
    var kid = derive(":theKid");
    fork(kid);
    return kid;
  }

  @Override public void merge() {
    var bors = downstream.get();
    RepoLike.super.merge();
    if (bors == null) return;
    this.definitions.putAll(bors.definitions);
    this.exports.putAll(bors.exports);
    this.modules.putAll(bors.modules);
  }
}
