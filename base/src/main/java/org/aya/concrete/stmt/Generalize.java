// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.ref.LocalVar;
import org.aya.concrete.Expr;
import org.aya.generic.ref.GeneralizedVar;
import org.aya.generic.ref.PreLevelVar;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;

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

  record Variables(
    @Override @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Param> variables
  ) implements Generalize {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitVariables(this, p);
    }
  }

  final class Param {
    public final @NotNull SourcePos sourcePos;
    public final @NotNull GeneralizedVar ref;
    public @NotNull Expr type;

    public Param(@NotNull SourcePos sourcePos, @NotNull GeneralizedVar ref, @NotNull Expr type) {
      this.sourcePos = sourcePos;
      this.ref = ref;
      this.type = type;
    }

    public @NotNull Expr.Param toExpr() {
      return new Expr.Param(sourcePos, new LocalVar(ref.name(), ref.sourcePos()), type, false, true);
    }
  }
}
