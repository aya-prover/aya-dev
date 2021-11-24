// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.trace;

import kala.collection.mutable.DynamicSeq;
import org.aya.api.ref.DefVar;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.core.term.Term;
import org.aya.tyck.ExprTycker;
import org.aya.util.TreeBuilder;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Deque;

/**
 * @author ice1000
 */
public sealed interface Trace extends TreeBuilder.Tree<Trace> {
  interface Visitor<P, R> {
    R visitExpr(@NotNull ExprT t, P p);
    R visitUnify(@NotNull UnifyT t, P p);
    R visitDecl(@NotNull DeclT t, P p);
    R visitTyck(@NotNull TyckT t, P p);
    R visitPat(@NotNull PatT t, P p);
    R visitLabel(@NotNull LabelT t, P p);
  }

  <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);

  final class Builder extends TreeBuilder<Trace> {
    @VisibleForTesting public @NotNull Deque<DynamicSeq<Trace>> getTops() {
      return tops;
    }
  }

  record LabelT(
    @NotNull SourcePos pos,
    @NotNull String label,
    @NotNull DynamicSeq<@NotNull Trace> children
  ) implements Trace {
    public LabelT(@NotNull SourcePos pos, @NotNull String label) {
      this(pos, label, DynamicSeq.create());
    }

    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitLabel(this, p);
    }
  }

  record DeclT(
    @NotNull DefVar<?, ?> var, @NotNull SourcePos pos,
    @NotNull DynamicSeq<@NotNull Trace> children
  ) implements Trace {
    public DeclT(@NotNull DefVar<?, ?> var, @NotNull SourcePos pos) {
      this(var, pos, DynamicSeq.create());
    }

    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitDecl(this, p);
    }
  }

  record ExprT(@NotNull Expr expr, @Nullable Term term, @NotNull DynamicSeq<@NotNull Trace> children) implements Trace {
    public ExprT(@NotNull Expr expr, @Nullable Term term) {
      this(expr, term, DynamicSeq.create());
    }

    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitExpr(this, p);
    }
  }

  record UnifyT(
    @NotNull Term lhs, @NotNull Term rhs,
    @NotNull SourcePos pos, @Nullable Term type,
    @NotNull DynamicSeq<@NotNull Trace> children
  ) implements Trace {
    public UnifyT(@NotNull Term lhs, @NotNull Term rhs, @NotNull SourcePos pos) {
      this(lhs, rhs, pos, null);
    }

    public UnifyT(@NotNull Term lhs, @NotNull Term rhs, @NotNull SourcePos pos, @Nullable Term type) {
      this(lhs, rhs, pos, type, DynamicSeq.create());
    }

    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitUnify(this, p);
    }
  }

  record TyckT(
    @NotNull Term term, @NotNull Term type,
    @NotNull SourcePos pos,
    @NotNull DynamicSeq<@NotNull Trace> children
  ) implements Trace {
    public TyckT(@NotNull ExprTycker.Result result, @NotNull SourcePos pos) {
      this(result.wellTyped(), result.type(), pos, DynamicSeq.create());
    }

    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitTyck(this, p);
    }
  }

  record PatT(
    @NotNull Term term, @NotNull Pattern pat,
    @NotNull SourcePos pos,
    @NotNull DynamicSeq<@NotNull Trace> children
  ) implements Trace {
    public PatT(@NotNull Term term, @NotNull Pattern pat, @NotNull SourcePos pos) {
      this(term, pat, pos, DynamicSeq.create());
    }

    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitPat(this, p);
    }
  }
}
