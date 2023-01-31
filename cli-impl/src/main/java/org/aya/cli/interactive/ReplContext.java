// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.interactive;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.utils.RepoLike;
import org.aya.concrete.stmt.Stmt;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.resolve.context.Context;
import org.aya.resolve.context.ModuleExport;
import org.aya.resolve.context.ModulePath;
import org.aya.resolve.context.PhysicalModuleContext;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ReplContext extends PhysicalModuleContext implements RepoLike<ReplContext> {
  private @Nullable ReplContext downstream = null;

  public ReplContext(@NotNull Context parent, @NotNull ImmutableSeq<String> name) {
    super(parent, name);
  }

  @Override public void addGlobal(
    boolean imported,
    @NotNull AnyVar ref,
    @NotNull ModulePath modName,
    @NotNull String name,
    Stmt.@NotNull Accessibility acc,
    @NotNull SourcePos sourcePos
  ) {
    // REPL always overwrites symbols.
    symbols().add(modName, name, ref);
    // REPL exports nothing, because nothing can import REPL.
  }

  @Override public boolean exportSymbol(@NotNull ModulePath modName, @NotNull String name, @NotNull DefVar<?, ?> ref) {
    super.exportSymbol(modName, name, ref);
    // REPL always overwrites symbols.
    return true;
  }

  @Override public void importModule(
    @NotNull ModulePath.Qualified modName,
    @NotNull ModuleExport mod,
    Stmt.@NotNull Accessibility accessibility,
    @NotNull SourcePos sourcePos
  ) {
    modules.put(modName, mod);
    if (accessibility == Stmt.Accessibility.Public) exports.set(modName, mod);
  }

  @Override public @NotNull ReplContext derive(@NotNull ImmutableSeq<@NotNull String> extraName) {
    return new ReplContext(this, this.moduleName().concat(extraName));
  }

  @Override public @NotNull ReplContext derive(@NotNull String extraName) {
    return new ReplContext(this, this.moduleName().appended(extraName));
  }

  @Override public void setDownstream(@Nullable ReplContext downstream) {
    this.downstream = downstream;
  }

  public @NotNull ReplContext fork() {
    var kid = derive(":theKid");
    fork(kid);
    return kid;
  }

  @Override public void merge() {
    var bors = downstream;
    RepoLike.super.merge();
    if (bors == null) return;
    this.symbols.table().putAll(bors.symbols.table());
    this.exports.putAll(bors.exports);
    this.modules.putAll(bors.modules);
  }

  @Contract(mutates = "this") public void clear() {
    modules.clear();
    exports.clear();
    symbols.table().clear();
  }
}
