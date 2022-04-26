// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.value.Ref;
import org.aya.cli.utils.RepoLike;
import org.aya.core.def.Def;
import org.aya.core.repr.AyaShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplShapeFactory extends AyaShape.Factory implements RepoLike<ReplShapeFactory> {
  private final @NotNull Ref<@Nullable ReplShapeFactory> downstream = new Ref<>();
  private final @Nullable ReplShapeFactory parent;

  public ReplShapeFactory(@Nullable ReplShapeFactory parent) {
    this.parent = parent;
  }

  @Override public @NotNull ImmutableSeq<Def> findImpl(@NotNull AyaShape shape) {
    var found = super.findImpl(shape);
    if (found.isNotEmpty()) return found;
    return parent == null ? ImmutableSeq.empty() : parent.findImpl(shape);
  }

  @Override public @NotNull Option<AyaShape> find(@NotNull Def def) {
    var found = super.find(def);
    if (found.isDefined()) return found;
    return parent == null ? Option.none() : parent.find(def);
  }

  @Override public @NotNull Ref<ReplShapeFactory> downstream() {
    return downstream;
  }

  public @NotNull ReplShapeFactory fork() {
    var kid = new ReplShapeFactory(this);
    fork(kid);
    return kid;
  }

  @Override public void merge() {
    var bors = downstream.get();
    RepoLike.super.merge();
    if (bors == null) return;
    bors.discovered.forEach((k, v) -> findImpl(v).forEach(old -> discovered.remove(old)));
    importAll(bors);
  }
}
