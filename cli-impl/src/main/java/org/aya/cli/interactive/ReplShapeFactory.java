// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.interactive;

import org.aya.primitive.ShapeFactory;
import org.aya.util.RepoLike;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplShapeFactory extends ShapeFactory implements RepoLike<ReplShapeFactory> {
  private @Nullable ReplShapeFactory downstream = null;

  @Override public void setDownstream(@Nullable ReplShapeFactory downstream) {
    this.downstream = downstream;
  }

  public @NotNull ReplShapeFactory fork() {
    var kid = new ReplShapeFactory();
    kid.importAll(this);
    fork(kid);
    return kid;
  }

  @Override public void merge() {
    var bors = downstream;
    RepoLike.super.merge();
    if (bors == null) return;
    importAll(bors);
  }
}
