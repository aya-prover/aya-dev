// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.ref.LocalVar;
import org.aya.concrete.Expr;
import org.aya.concrete.resolve.context.Context;
import org.aya.generic.ref.GeneralizedVar;
import org.aya.generic.ref.PreLevelVar;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface Generalize extends Stmt {
  @Override default @NotNull Accessibility accessibility() {
    return Accessibility.Private;
  }

  record Levels(
    @Override @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<WithPos<PreLevelVar>> levels
  ) implements Generalize {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitLevels(this, p);
    }
  }

  final class Variables implements Generalize {
    public final @NotNull SourcePos sourcePos;
    public final @NotNull ImmutableSeq<GeneralizedVar> variables;
    public @NotNull Expr type;
    public @Nullable Context ctx = null;

    public Variables(@NotNull SourcePos sourcePos, @NotNull ImmutableSeq<GeneralizedVar> variables, @NotNull Expr type) {
      this.sourcePos = sourcePos;
      this.variables = variables;
      this.type = type;
      variables.forEach(variable -> variable.owner = this);
    }

    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitVariables(this, p);
    }

    public @NotNull Expr.Param toExpr(boolean explicit, @NotNull LocalVar ref) {
      return new Expr.Param(sourcePos, ref, type, false, explicit);
    }

    public @NotNull ImmutableSeq<Expr.Param> toExpr() {
      return variables.map(one -> toExpr(true, one.toLocal()));
    }

    public @NotNull SourcePos sourcePos() {
      return sourcePos;
    }
  }
}
