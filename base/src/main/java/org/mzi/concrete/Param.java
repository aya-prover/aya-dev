// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.error.SourcePos;
import org.mzi.ref.LocalVar;

import java.util.function.Function;

/**
 * @author re-xyr
 */
public record Param(
  @NotNull SourcePos sourcePos,
  @NotNull LocalVar var,
  @Nullable Expr type,
  boolean explicit
) {
  public Param(@NotNull SourcePos sourcePos, @NotNull LocalVar var, boolean explicit) {
    this(sourcePos, var, null, explicit);
  }

  public @NotNull Param mapExpr(@NotNull Function<@Nullable Expr, @Nullable Expr> mapper) {
    return new Param(sourcePos, var, mapper.apply(type), explicit);
  }
}
