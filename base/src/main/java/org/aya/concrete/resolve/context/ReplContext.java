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

public record ReplContext(
  @Override @NotNull Context parent,
  @Override @NotNull MutableMap<String, MutableMap<Seq<String>, Var>> definitions,
  @Override @NotNull MutableMap<ImmutableSeq<String>, MutableMap<String, Var>> modules
) implements ModuleContext {
  public ReplContext(@NotNull Context parent) {
    this(parent, MutableMap.create(), MutableMap.create());
  }

  @Override
  public void addGlobal(@NotNull ImmutableSeq<String> modName, @NotNull String name, Stmt.@NotNull Accessibility accessibility, @NotNull Var ref, @NotNull SourcePos sourcePos) {
    definitions.getOrPut(name, MutableHashMap::of).set(modName, ref);
  }
}
