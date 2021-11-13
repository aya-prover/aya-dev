// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import org.aya.api.concrete.ConcretePat;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.api.util.WithPos;
import org.aya.distill.BaseDistiller;
import org.aya.distill.ConcreteDistiller;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author kiva, ice1000
 */
public sealed interface Pattern extends ConcretePat {
  @Override default @NotNull Doc toDoc(@NotNull DistillerOptions options) {
    return accept(new ConcreteDistiller(options), BaseDistiller.Outer.Free);
  }

  default <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    visitor.traceEntrance(this, p);
    var ret = doAccept(visitor, p);
    visitor.traceExit(ret, this, p);
    return ret;
  }

  <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p);

  interface Visitor<P, R> {
    R visitTuple(@NotNull Tuple tuple, P p);
    R visitNumber(@NotNull Number number, P p);
    R visitAbsurd(@NotNull Absurd absurd, P p);
    R visitBind(@NotNull Bind bind, P p);
    R visitCalmFace(@NotNull CalmFace calmFace, P p);
    R visitCtor(@NotNull Ctor ctor, P p);
    default void traceEntrance(@NotNull Pattern pat, P p) {
    }
    default void traceExit(R r, @NotNull Pattern pat, P p) {
    }
  }

  record Tuple(
    @NotNull SourcePos sourcePos,
    boolean explicit,
    @NotNull ImmutableSeq<Pattern> patterns,
    @Nullable LocalVar as
  ) implements Pattern {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitTuple(this, p);
    }
  }

  record Number(
    @NotNull SourcePos sourcePos,
    boolean explicit,
    int number
  ) implements Pattern {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitNumber(this, p);
    }
  }

  record Absurd(
    @NotNull SourcePos sourcePos,
    boolean explicit
  ) implements Pattern {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitAbsurd(this, p);
    }
  }

  record CalmFace(
    @NotNull SourcePos sourcePos,
    boolean explicit
  ) implements Pattern {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitCalmFace(this, p);
    }
  }

  record Bind(
    @NotNull SourcePos sourcePos,
    boolean explicit,
    @NotNull LocalVar bind
  ) implements Pattern {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitBind(this, p);
    }
  }

  record Ctor(
    @NotNull SourcePos sourcePos,
    boolean explicit,
    @NotNull WithPos<@NotNull Var> resolved,
    @NotNull ImmutableSeq<Pattern> params,
    @Nullable LocalVar as
  ) implements Pattern {
    public Ctor(@NotNull Pattern.Bind bind, @NotNull Var maybe) {
      this(bind.sourcePos(), bind.explicit(), new WithPos<>(bind.sourcePos(), maybe), ImmutableSeq.empty(), null);
    }

    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitCtor(this, p);
    }
  }

  /**
   * @author kiva, ice1000
   */
  final class Clause {
    public final @NotNull SourcePos sourcePos;
    public final @NotNull ImmutableSeq<Pattern> patterns;
    public @NotNull Option<Expr> expr;
    public boolean hasError = false;

    public Clause(@NotNull SourcePos sourcePos, @NotNull ImmutableSeq<Pattern> patterns, @NotNull Option<Expr> expr) {
      this.sourcePos = sourcePos;
      this.patterns = patterns;
      this.expr = expr;
    }
  }
}
