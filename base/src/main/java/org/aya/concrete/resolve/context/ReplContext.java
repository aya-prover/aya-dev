// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve.context;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.concrete.stmt.Stmt;
import org.jetbrains.annotations.NotNull;

public final class ReplContext implements ModuleContext {
  public final @NotNull Context parent;
  public final @NotNull MutableMap<String, MutableMap<Seq<String>, Var>> definitions = MutableHashMap.of();
  public final @NotNull MutableMap<ImmutableSeq<String>, MutableMap<String, Var>> modules = MutableHashMap.of();

  public ReplContext(@NotNull Context parent) {
    this.parent = parent;
  }

  @Override
  public @NotNull Context parent() {
    return parent;
  }

  @Override
  public @NotNull MutableMap<String, MutableMap<Seq<String>, Var>> definitions() {
    return definitions;
  }

  @Override
  public @NotNull MutableMap<ImmutableSeq<String>, MutableMap<String, Var>> modules() {
    return modules;
  }

  @Override
  public void addGlobal(@NotNull ImmutableSeq<String> modName, @NotNull String name, Stmt.@NotNull Accessibility accessibility, @NotNull Var ref, @NotNull SourcePos sourcePos) {
    if (!definitions.containsKey(name))
      definitions.set(name, MutableHashMap.of());
    definitions.get(name).set(modName, ref);
  }
}
