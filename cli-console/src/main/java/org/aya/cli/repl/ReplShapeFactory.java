// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import kala.value.MutableValue;
import org.aya.cli.utils.RepoLike;
import org.aya.core.repr.AyaShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplShapeFactory extends AyaShape.Factory implements RepoLike<ReplShapeFactory> {
  private final @NotNull MutableValue<@Nullable ReplShapeFactory> downstream = MutableValue.create();

  @Override public @NotNull MutableValue<ReplShapeFactory> downstream() {
    return downstream;
  }

  public @NotNull ReplShapeFactory fork() {
    var kid = new ReplShapeFactory();
    kid.importAll(this);
    fork(kid);
    return kid;
  }

  @Override public void merge() {
    var bors = downstream.get();
    RepoLike.super.merge();
    if (bors == null) return;
    importAll(bors);
  }
}
