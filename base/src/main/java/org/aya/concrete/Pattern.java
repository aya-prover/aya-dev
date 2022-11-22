// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.value.MutableValue;
import org.aya.core.def.CtorDef;
import org.aya.core.repr.ShapeRecognition;
import org.aya.core.term.DataCall;
import org.aya.core.term.Term;
import org.aya.distill.BaseDistiller;
import org.aya.distill.ConcreteDistiller;
import org.aya.generic.AyaDocile;
import org.aya.generic.Shaped;
import org.aya.pretty.doc.Doc;
import org.aya.ref.AnyVar;
import org.aya.ref.LocalVar;
import org.aya.util.Arg;
import org.aya.util.ForLSP;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourceNode;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

/**
 * Patterns in the concrete syntax.
 *
 * @author kiva, ice1000, HoshinoTented
 */
public sealed interface Pattern extends AyaDocile, SourceNode {
  @Override default @NotNull Doc toDoc(@NotNull DistillerOptions options) {
    return new ConcreteDistiller(options).pattern(this, true, BaseDistiller.Outer.Free);
  }

  default @NotNull Pattern descent(@NotNull UnaryOperator<@NotNull Pattern> f) {
    return switch (this) {
      case Pattern.BinOpSeq(var pos, var seq, var as) -> new Pattern.BinOpSeq(pos, descent(f, seq), as);
      case Pattern.Ctor(var pos, var resolved, var params, var as) ->
        new Pattern.Ctor(pos, resolved, descent(f, params), as);
      case Pattern.Tuple(var pos, var patterns, var as) -> new Pattern.Tuple(pos, descent(f, patterns), as);
      case Pattern.List(var pos, var patterns, var as) -> new Pattern.List(pos, patterns.map(f), as);
      default -> this;
    };
  }
  private static @NotNull ImmutableSeq<Arg<Pattern>>
  descent(@NotNull UnaryOperator<@NotNull Pattern> f, @NotNull ImmutableSeq<Arg<Pattern>> seq) {
    return seq.map(x -> x.descent(f));
  }

  record Tuple(
    @Override @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Arg<Pattern>> patterns,
    @Nullable LocalVar as
  ) implements Pattern {
  }

  record Number(@Override @NotNull SourcePos sourcePos, int number) implements Pattern {}

  record Absurd(@Override @NotNull SourcePos sourcePos) implements Pattern {}

  record CalmFace(@Override @NotNull SourcePos sourcePos) implements Pattern {}

  /**
   * @param userType only generated when a typed lambda is pushed into the patterns
   * @param type used in the LSP server
   */
  record Bind(
    @NotNull SourcePos sourcePos,
    @NotNull LocalVar bind,
    @Nullable Expr userType,
    @ForLSP @NotNull MutableValue<@Nullable Term> type
  ) implements Pattern {
    public Bind(@NotNull SourcePos sourcePos, @NotNull LocalVar bind) {
      this(sourcePos, bind, null, MutableValue.create());
    }
  }

  record Ctor(
    @Override @NotNull SourcePos sourcePos,
    @NotNull WithPos<@NotNull AnyVar> resolved,
    @NotNull ImmutableSeq<Arg<Pattern>> params,
    @Nullable LocalVar as
  ) implements Pattern {
    public Ctor(@NotNull Pattern.Bind bind, @NotNull AnyVar maybe) {
      this(bind.sourcePos(), new WithPos<>(bind.sourcePos(), maybe), ImmutableSeq.empty(), null);
    }
  }

  record BinOpSeq(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Arg<Pattern>> seq,
    @Nullable LocalVar as
  ) implements Pattern {
  }

  /** Sugared List Pattern */
  record List(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Pattern> elements,
    @Nullable LocalVar as
  ) implements Pattern {
  }

  /**
   * @author kiva, ice1000
   */
  final class Clause {
    public final @NotNull SourcePos sourcePos;
    public final @NotNull ImmutableSeq<Arg<Pattern>> patterns;
    public final @NotNull Option<Expr> expr;
    public boolean hasError = false;

    public Clause(@NotNull SourcePos sourcePos, @NotNull ImmutableSeq<Arg<Pattern>> patterns, @NotNull Option<Expr> expr) {
      this.sourcePos = sourcePos;
      this.patterns = patterns;
      this.expr = expr;
    }

    public @NotNull Clause update(@NotNull ImmutableSeq<Arg<Pattern>> pats, @NotNull Option<Expr> body) {
      return body.sameElements(expr, true) && pats.sameElements(patterns, true) ? this
        : new Clause(sourcePos, pats, body);
    }

    public @NotNull Clause descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(patterns, expr.map(f));
    }

    public @NotNull Clause descent(@NotNull UnaryOperator<@NotNull Expr> f, @NotNull UnaryOperator<@NotNull Pattern> g) {
      return update(Pattern.descent(g, patterns), expr.map(f));
    }
  }

  record FakeShapedList(
    @NotNull SourcePos sourcePos,
    @Nullable LocalVar as,
    @Override @NotNull ImmutableSeq<Pattern> repr,
    @Override @NotNull ShapeRecognition recognition,
    @Override @NotNull DataCall type
  ) implements Shaped.List<Pattern> {
    @Override public @NotNull Pattern makeNil(@NotNull CtorDef nil, @NotNull Arg<Term> type) {
      return new Pattern.Ctor(sourcePos, new WithPos<>(sourcePos, nil.ref()), ImmutableSeq.empty(), as);
    }

    @Override public @NotNull Pattern
    makeCons(@NotNull CtorDef cons, @NotNull Arg<Term> type, Arg<Pattern> x, Arg<Pattern> xs) {
      // x    : Current Pattern
      // xs   : Right Pattern
      // Goal : consCtor x xs
      return new Pattern.Ctor(sourcePos, new WithPos<>(sourcePos, cons.ref()), ImmutableSeq.of(x, xs), as);
    }

    @Override public @NotNull Pattern destruct(@NotNull ImmutableSeq<Pattern> repr) {
      return new FakeShapedList(sourcePos, null, repr, recognition, type).constructorForm();
    }
  }
}
