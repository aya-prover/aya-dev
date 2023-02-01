// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.value.MutableValue;
import org.aya.concrete.stmt.QualifiedID;
import org.aya.core.def.CtorDef;
import org.aya.core.repr.ShapeRecognition;
import org.aya.core.term.DataCall;
import org.aya.core.term.Term;
import org.aya.prettier.BasePrettier;
import org.aya.prettier.ConcretePrettier;
import org.aya.generic.AyaDocile;
import org.aya.generic.Shaped;
import org.aya.pretty.doc.Doc;
import org.aya.ref.AnyVar;
import org.aya.ref.LocalVar;
import org.aya.util.Arg;
import org.aya.util.ForLSP;
import org.aya.util.prettier.PrettierOptions;
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
  @Override default @NotNull Doc toDoc(@NotNull PrettierOptions options) {
    return new ConcretePrettier(options).pattern(this, true, BasePrettier.Outer.Free);
  }

  default @NotNull Pattern descent(@NotNull UnaryOperator<@NotNull Pattern> f) {
    return switch (this) {
      case Pattern.BinOpSeq(var pos, var seq) -> new Pattern.BinOpSeq(pos, descent(f, seq));
      case Pattern.Ctor(var pos, var resolved, var params) -> new Pattern.Ctor(pos, resolved, descent(f, params));
      case Pattern.Tuple(var pos, var patterns) -> new Pattern.Tuple(pos, descent(f, patterns));
      case Pattern.List(var pos, var patterns) -> new Pattern.List(pos, patterns.map(f));
      case Pattern.As(var pos, var inner, var as, var type) -> new Pattern.As(pos, f.apply(inner), as, type);
      default -> this;
    };
  }
  private static @NotNull ImmutableSeq<Arg<Pattern>>
  descent(@NotNull UnaryOperator<@NotNull Pattern> f, @NotNull ImmutableSeq<Arg<Pattern>> seq) {
    return seq.map(x -> x.descent(f));
  }

  record Tuple(
    @Override @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Arg<Pattern>> patterns
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

  /**
   * @param qualifiedID a qualified QualifiedID ({@code isUnqualified == false})
   */
  record QualifiedRef(
    @NotNull SourcePos sourcePos,
    @NotNull QualifiedID qualifiedID,
    @Nullable Expr userType,
    @ForLSP @NotNull MutableValue<@Nullable Term> type
  ) implements Pattern {
    public QualifiedRef(@NotNull SourcePos sourcePos, @NotNull QualifiedID qualifiedID) {
      this(sourcePos, qualifiedID, null, MutableValue.create());
    }
  }

  record Ctor(
    @Override @NotNull SourcePos sourcePos,
    @NotNull WithPos<@NotNull AnyVar> resolved,
    @NotNull ImmutableSeq<Arg<Pattern>> params
  ) implements Pattern {
    public Ctor(@NotNull Pattern.Bind bind, @NotNull AnyVar maybe) {
      this(bind.sourcePos(), new WithPos<>(bind.sourcePos(), maybe), ImmutableSeq.empty());
    }
    public Ctor(@NotNull Pattern.QualifiedRef qref, @NotNull AnyVar maybe) {
      this(qref.sourcePos(), new WithPos<>(qref.sourcePos(), maybe), ImmutableSeq.empty());
    }
  }

  record BinOpSeq(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Arg<Pattern>> seq
  ) implements Pattern {
  }

  /**
   * Represent a {@code (Pattern) as bind} pattern
   */
  record As(
    @Override @NotNull SourcePos sourcePos,
    @NotNull Pattern pattern,
    @NotNull LocalVar as,
    @ForLSP @NotNull MutableValue<@Nullable Term> type
  ) implements Pattern {
    public static Arg<Pattern> wrap(@NotNull SourcePos pos, @NotNull Arg<Pattern> pattern, @NotNull LocalVar var) {
      return new Arg<>(new As(pos, pattern.term(), var, MutableValue.create()), pattern.explicit());
    }
  }

  /** Sugared List Pattern */
  record List(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Pattern> elements
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
    @Override @NotNull ImmutableSeq<Pattern> repr,
    @Override @NotNull ShapeRecognition recognition,
    @Override @NotNull DataCall type
  ) implements Shaped.List<Pattern> {
    @Override public @NotNull Pattern makeNil(@NotNull CtorDef nil, @NotNull Arg<Term> type) {
      return new Pattern.Ctor(sourcePos, new WithPos<>(sourcePos, nil.ref()), ImmutableSeq.empty());
    }

    @Override public @NotNull Pattern
    makeCons(@NotNull CtorDef cons, @NotNull Arg<Term> type, Arg<Pattern> x, Arg<Pattern> xs) {
      // x    : Current Pattern
      // xs   : Right Pattern
      // Goal : consCtor x xs
      return new Pattern.Ctor(sourcePos, new WithPos<>(sourcePos, cons.ref()), ImmutableSeq.of(x, xs));
    }

    @Override public @NotNull Pattern destruct(@NotNull ImmutableSeq<Pattern> repr) {
      return new FakeShapedList(sourcePos, repr, recognition, type).constructorForm();
    }
  }
}
