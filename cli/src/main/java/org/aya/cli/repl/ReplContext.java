// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import org.aya.concrete.stmt.Stmt;
import org.aya.ref.Var;
import org.aya.resolve.context.Context;
import org.aya.resolve.context.PhysicalModuleContext;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public final class ReplContext extends PhysicalModuleContext {
  public ReplContext(@NotNull Context parent, @NotNull ImmutableSeq<String> name) {
    super(parent, name);
  }

  @Override
  public void addGlobal(@NotNull ImmutableSeq<String> modName, @NotNull String name, Stmt.@NotNull Accessibility accessibility, @NotNull Var ref, @NotNull SourcePos sourcePos) {
    definitions.getOrPut(name, MutableHashMap::of).set(modName, ref);
    if (accessibility == Stmt.Accessibility.Public) exports.get(TOP_LEVEL_MOD_NAME).set(name, ref);
  }

  @Override
  public void importModule(Stmt.@NotNull Accessibility accessibility, @NotNull SourcePos sourcePos, ImmutableSeq<String> componentName, MutableMap<String, Var> mod) {
    modules.put(componentName, mod);
    if (accessibility == Stmt.Accessibility.Public) exports.set(componentName, mod);
  }

  @Override
  public @NotNull PhysicalModuleContext derive(@NotNull Seq<@NotNull String> extraName) {
    return new ReplContext(this, this.moduleName().concat(extraName));
  }

  @Override
  public @NotNull PhysicalModuleContext derive(@NotNull String extraName) {
    return new ReplContext(this, this.moduleName().appended(extraName));
  }
}
