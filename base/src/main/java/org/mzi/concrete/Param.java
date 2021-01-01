// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete;

import asia.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.error.SourcePos;
import org.mzi.api.ref.Var;

/**
 * @author re-xyr
 */
public record Param(
  @NotNull SourcePos sourcePos,
  @NotNull Var var,
  @Nullable Expr type,
  boolean explicit
) {
  public Param(@NotNull SourcePos sourcePos, @NotNull Var var, boolean explicit) {
    this(sourcePos, var, null, explicit);
  }
}
