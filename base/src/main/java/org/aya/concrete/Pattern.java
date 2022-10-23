// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.value.MutableValue;
import org.aya.core.def.CtorDef;
import org.aya.core.repr.AyaShape;
import org.aya.core.term.Term;
import org.aya.distill.BaseDistiller;
import org.aya.distill.ConcreteDistiller;
import org.aya.generic.Arg;
import org.aya.generic.AyaDocile;
import org.aya.generic.Shaped;
import org.aya.pretty.doc.Doc;
import org.aya.ref.AnyVar;
import org.aya.ref.LocalVar;
import org.aya.util.ForLSP;
import org.aya.util.binop.BinOpParser;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourceNode;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author kiva, ice1000
 */
public sealed interface Pattern extends AyaDocile, SourceNode, BinOpParser.Elem<Pattern> {
  @Override default @NotNull Doc toDoc(@NotNull DistillerOptions options) {
    return new ConcreteDistiller(options).pattern(this, BaseDistiller.Outer.Free);
  }

  @Override @NotNull default Pattern expr() {
    return this;
  }

  record Tuple(
    @Override @NotNull SourcePos sourcePos,
    boolean explicit,
    @NotNull ImmutableSeq<Pattern> patterns,
    @Nullable LocalVar as
  ) implements Pattern {
  }

  record Number(
    @Override @NotNull SourcePos sourcePos,
    boolean explicit,
    int number
  ) implements Pattern {
  }

  record Absurd(
    @Override @NotNull SourcePos sourcePos,
    boolean explicit
  ) implements Pattern {
  }

  record CalmFace(
    @Override @NotNull SourcePos sourcePos,
    boolean explicit
  ) implements Pattern {
  }

  record Bind(
    @NotNull SourcePos sourcePos,
    boolean explicit,
    @NotNull LocalVar bind,
    @ForLSP @NotNull MutableValue<@Nullable Term> type
  ) implements Pattern {
  }

  record Ctor(
    @Override @NotNull SourcePos sourcePos,
    boolean explicit,
    @NotNull WithPos<@NotNull AnyVar> resolved,
    @NotNull ImmutableSeq<Pattern> params,
    @Nullable LocalVar as
  ) implements Pattern {
    public Ctor(@NotNull Pattern.Bind bind, @NotNull AnyVar maybe) {
      this(bind.sourcePos(), bind.explicit(), new WithPos<>(bind.sourcePos(), maybe), ImmutableSeq.empty(), null);
    }
  }

  record BinOpSeq(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Pattern> seq,
    @Nullable LocalVar as,
    boolean explicit
  ) implements Pattern {
  }

  /**
   * <h1>Undesugared List Pattern</h1>
   *
   * @param sourcePos
   * @param explicit
   * @param elements
   * @param as
   */
  record List(
    @NotNull SourcePos sourcePos,
    boolean explicit,
    @NotNull ImmutableSeq<Pattern> elements,
    @Nullable LocalVar as
  ) implements Pattern {
  }

  /**
   * @author kiva, ice1000
   */
  final class Clause {
    public final @NotNull SourcePos sourcePos;
    public final @NotNull ImmutableSeq<Pattern> patterns;
    public final @NotNull Option<Expr> expr;
    public boolean hasError = false;

    public Clause(@NotNull SourcePos sourcePos, @NotNull ImmutableSeq<Pattern> patterns, @NotNull Option<Expr> expr) {
      this.sourcePos = sourcePos;
      this.patterns = patterns;
      this.expr = expr;
    }
  }

  record FakeShapedList(
    @NotNull SourcePos sourcePos,
    boolean explicit,
    @Nullable LocalVar as,
    @Override @NotNull ImmutableSeq<Pattern> repr,
    @Override @NotNull AyaShape shape,
    @Override @NotNull Term type
  ) implements Shaped.List<Pattern> {
    @Override public @NotNull Pattern makeNil(@NotNull CtorDef nil, @NotNull Arg<Term> type) {
      return new Pattern.Ctor(sourcePos, explicit,
        new WithPos<>(sourcePos, nil.ref()), ImmutableSeq.empty(), as);
    }

    @Override public @NotNull Pattern
    makeCons(@NotNull CtorDef cons, @NotNull Arg<Term> type, Pattern x, Pattern last) {
      // x    : Current Pattern
      // xs   : Right Pattern
      // Goal : consCtor value list
      return new Pattern.Ctor(sourcePos, explicit,
        new WithPos<>(sourcePos, cons.ref()),
        ImmutableSeq.of(x, last), as);
    }

    @Override public @NotNull Pattern destruct(@NotNull ImmutableSeq<Pattern> repr) {
      return new FakeShapedList(sourcePos, true, null, repr, shape, type)
        .constructorForm();
    }
  }
}
