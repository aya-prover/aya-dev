// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete;

import org.aya.api.concrete.ConcretePat;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.concrete.visitor.ConcreteDistiller;
import org.aya.pretty.doc.Doc;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.control.Option;
import org.glavo.kala.value.Ref;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author kiva, ice1000
 */
@Debug.Renderer(text = "toDoc().debugRender()")
public sealed interface Pattern extends ConcretePat {
  @Override default @NotNull Doc toDoc() {
    return accept(ConcreteDistiller.INSTANCE, false);
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

  /**
   * @param resolved will be modified during resolving
   */
  record Bind(
    @NotNull SourcePos sourcePos,
    boolean explicit,
    @NotNull LocalVar bind,
    @NotNull Ref<@Nullable Var> resolved
  ) implements Pattern {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitBind(this, p);
    }
  }

  record Ctor(
    @NotNull SourcePos sourcePos,
    boolean explicit,
    @NotNull String name,
    @NotNull ImmutableSeq<Pattern> params,
    @Nullable LocalVar as
  ) implements Pattern {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitCtor(this, p);
    }
  }

  /**
   * @author kiva, ice1000
   */
  record Clause(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Pattern> patterns,
    @NotNull Option<Expr> expr
  ) {
  }
}
