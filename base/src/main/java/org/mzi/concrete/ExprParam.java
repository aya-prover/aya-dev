// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.error.SourcePos;
import org.mzi.generic.Param;
import org.mzi.ref.LocalVar;

import java.util.function.Function;

/**
 * @author re-xyr
 */
public record ExprParam(
  @NotNull SourcePos sourcePos,
  @NotNull LocalVar ref,
  @Nullable Expr type,
  boolean explicit
) implements Param<Expr> {
  public ExprParam(@NotNull SourcePos sourcePos, @NotNull LocalVar var, boolean explicit) {
    this(sourcePos, var, null, explicit);
  }

  public @NotNull ExprParam mapExpr(@NotNull Function<@Nullable Expr, @Nullable Expr> mapper) {
    return new ExprParam(sourcePos, ref, mapper.apply(type), explicit);
  }
}
