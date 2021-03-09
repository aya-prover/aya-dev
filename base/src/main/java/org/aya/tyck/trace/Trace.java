// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.trace;

import org.aya.api.error.SourcePos;
import org.aya.api.ref.DefVar;
import org.aya.concrete.Expr;
import org.aya.core.term.Term;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * @author ice1000
 */
public sealed interface Trace {
  @NotNull Buffer<@NotNull Trace> subtraces();

  interface Visitor<P, R> {
    R visitExpr(@NotNull ExprT t, P p);
    R visitUnify(@NotNull UnifyT t, P p);
    R visitDecl(@NotNull DeclT t, P p);
    R visitTyck(@NotNull TyckT t, P p);
  }

  <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);

  class Builder {
    @VisibleForTesting
    public Deque<@NotNull Buffer<@NotNull Trace>> tops = new ArrayDeque<>();

    public @NotNull Buffer<@NotNull Trace> root() {
      return tops.getFirst();
    }

    {
      tops.addLast(Buffer.of());
    }

    public void shift(@NotNull Trace trace) {
      Objects.requireNonNull(tops.getLast()).append(trace);
      tops.addLast(trace.subtraces());
    }

    public void reduce() {
      tops.removeLast();
    }
  }

  record DeclT(@NotNull DefVar<?, ?> var, @NotNull SourcePos pos, @NotNull Buffer<@NotNull Trace> subtraces) implements Trace {
    public DeclT(@NotNull DefVar<?, ?> var, @NotNull SourcePos pos) {
      this(var, pos, Buffer.of());
    }

    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitDecl(this, p);
    }
  }

  record ExprT(@NotNull Expr expr, @Nullable Term term, @NotNull Buffer<@NotNull Trace> subtraces) implements Trace {
    public ExprT(@NotNull Expr expr, @Nullable Term term) {
      this(expr, term, Buffer.of());
    }

    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitExpr(this, p);
    }
  }

  record UnifyT(@NotNull Term lhs, @NotNull Term rhs, @NotNull SourcePos pos, @NotNull Buffer<@NotNull Trace> subtraces) implements Trace {
    public UnifyT(@NotNull Term lhs, @NotNull Term rhs, @NotNull SourcePos pos) {
      this(lhs, rhs, pos, Buffer.of());
    }

    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitUnify(this, p);
    }
  }

  record TyckT(@NotNull Term term, @NotNull Term type, @NotNull SourcePos pos, @NotNull Buffer<@NotNull Trace> subtraces) implements Trace {
    public TyckT(@NotNull Term term, @NotNull Term type, @NotNull SourcePos pos) {
      this (term, type, pos, Buffer.of());
    }

    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitTyck(this, p);
    }
  }
}
