// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete.stmt;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.stmt.TyckUnit;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.ref.GeneralizedVar;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.position.PosedUnaryOperator;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
import org.jetbrains.annotations.NotNull;

public final class Generalize implements Stmt, TyckUnit {
  @Override public @NotNull Accessibility accessibility() {
    return Accessibility.Private;
  }
  @Override public void descentInPlace(@NotNull PosedUnaryOperator<Expr> f, @NotNull PosedUnaryOperator<Pattern> p) {
    type = type.descent(f);
  }

  public final @NotNull SourcePos sourcePos;
  public final @NotNull ImmutableSeq<GeneralizedVar> variables;
  public @NotNull WithPos<Expr> type;

  public Generalize(
    @NotNull SourcePos sourcePos, @NotNull ImmutableSeq<GeneralizedVar> variables,
    @NotNull WithPos<Expr> type
  ) {
    this.sourcePos = sourcePos;
    this.variables = variables;
    this.type = type;
    variables.forEach(variable -> variable.owner = this);
  }

  public @NotNull Expr.Param toExpr(boolean explicit, @NotNull LocalVar ref) {
    return new Expr.Param(ref.definition(), ref, type, explicit);
  }

  public @NotNull ImmutableSeq<Expr.Param> toExpr() {
    return variables.map(one -> toExpr(true, one.toLocal()));
  }

  public @NotNull SourcePos sourcePos() { return sourcePos; }

  @Override public @NotNull SourcePos nameSourcePos() {
    return variables.map(GeneralizedVar::sourcePos).reduce(SourcePos::union);
  }
}
