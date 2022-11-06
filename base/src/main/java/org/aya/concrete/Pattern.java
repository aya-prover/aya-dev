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
import org.aya.generic.util.InternalException;
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

import java.util.function.Function;
import java.util.function.UnaryOperator;

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

  @NotNull Pattern licitify(boolean explicit);

  default @NotNull Pattern descent(@NotNull Function<@NotNull Pattern, @NotNull Pattern> f) {
    return switch (this) {
      case Pattern.BinOpSeq(var pos, var seq, var as, var ex) -> new Pattern.BinOpSeq(pos, seq.map(f), as, ex);
      case Pattern.Ctor(var pos, var licit, var resolved, var params, var as) ->
        new Pattern.Ctor(pos, licit, resolved, params.map(f), as);
      case Pattern.Tuple(var pos, var licit, var patterns, var as) ->
        new Pattern.Tuple(pos, licit, patterns.map(f), as);
      case Pattern.List(var pos, var licit, var patterns, var as) -> new Pattern.List(pos, licit, patterns.map(f), as);
      default -> this;
    };
  }

  record Tuple(
    @Override @NotNull SourcePos sourcePos,
    boolean explicit,
    @NotNull ImmutableSeq<Pattern> patterns,
    @Nullable LocalVar as
  ) implements Pattern {
    @Override
    public @NotNull Pattern licitify(boolean explicit) {
      return new Tuple(sourcePos, explicit, patterns, as);
    }
  }

  record Number(
    @Override @NotNull SourcePos sourcePos,
    boolean explicit,
    int number
  ) implements Pattern {
    @Override
    public @NotNull Pattern licitify(boolean explicit) {
      return new Number(sourcePos, explicit, number);
    }
  }

  record Absurd(
    @Override @NotNull SourcePos sourcePos,
    boolean explicit
  ) implements Pattern {
    @Override
    public @NotNull Pattern licitify(boolean explicit) {
      return new Absurd(sourcePos, explicit);
    }
  }

  record CalmFace(
    @Override @NotNull SourcePos sourcePos,
    boolean explicit
  ) implements Pattern {
    @Override
    public @NotNull Pattern licitify(boolean explicit) {
      return new CalmFace(sourcePos, explicit);
    }
  }

  record Bind(
    @NotNull SourcePos sourcePos,
    boolean explicit,
    @NotNull LocalVar bind,
    @ForLSP @NotNull MutableValue<@Nullable Term> type
  ) implements Pattern {
    @Override
    public @NotNull Pattern licitify(boolean explicit) {
      return new Bind(sourcePos, explicit, bind, type);
    }
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

    @Override
    public @NotNull Pattern licitify(boolean explicit) {
      return new Ctor(sourcePos, explicit, resolved, params, as);
    }
  }

  record BinOpSeq(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Pattern> seq,
    @Nullable LocalVar as,
    boolean explicit
  ) implements Pattern {
    @Override
    public @NotNull Pattern licitify(boolean explicit) {
      throw new InternalException("unreachable");
    }
  }

  /** Sugared List Pattern */
  record List(
    @NotNull SourcePos sourcePos,
    boolean explicit,
    @NotNull ImmutableSeq<Pattern> elements,
    @Nullable LocalVar as
  ) implements Pattern {
    @Override
    public @NotNull Pattern licitify(boolean explicit) {
      return new List(sourcePos, explicit, elements, as);
    }
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

    public @NotNull Clause update(@NotNull ImmutableSeq<Pattern> pats, @NotNull Option<Expr> body) {
      return body.sameElements(expr, true) && pats.sameElements(patterns, true) ? this
        : new Clause(sourcePos, pats, body);
    }

    public @NotNull Clause descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(patterns, expr.map(f));
    }

    public @NotNull Clause descent(@NotNull UnaryOperator<@NotNull Expr> f, @NotNull UnaryOperator<@NotNull Pattern> g) {
      return update(patterns.map(g), expr.map(f));
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
    makeCons(@NotNull CtorDef cons, @NotNull Arg<Term> type, Pattern x, Pattern xs) {
      var xLicit = cons.selfTele.get(0).explicit();
      var xsLicit = cons.selfTele.get(1).explicit();

      x = x.licitify(xLicit);
      xs = xs.licitify(xsLicit);

      // x    : Current Pattern
      // xs   : Right Pattern
      // Goal : consCtor x xs
      return new Pattern.Ctor(sourcePos, explicit,
        new WithPos<>(sourcePos, cons.ref()),
        ImmutableSeq.of(x, xs), as);
    }

    @Override public @NotNull Pattern destruct(@NotNull ImmutableSeq<Pattern> repr) {
      return new FakeShapedList(sourcePos, explicit, null, repr, shape, type)
        .constructorForm();
    }
  }
}
