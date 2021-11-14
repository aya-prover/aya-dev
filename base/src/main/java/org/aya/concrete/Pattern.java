// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import org.aya.api.concrete.ConcretePat;
import org.aya.api.distill.AyaDocile;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.distill.BaseDistiller;
import org.aya.distill.ConcreteDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.util.binop.BinOpParser;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author kiva, ice1000
 */
public sealed interface Pattern extends ConcretePat, BinOpParser.Elem<Pattern> {
  @Override default @NotNull Doc toDoc(@NotNull DistillerOptions options) {
    return new ConcreteDistiller(options).visitPattern(this, BaseDistiller.Outer.Free);
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
    @NotNull LocalVar bind
  ) implements Pattern {
  }

  record Ctor(
    @Override @NotNull SourcePos sourcePos,
    boolean explicit,
    @NotNull WithPos<@NotNull Var> resolved,
    @NotNull ImmutableSeq<Pattern> params,
    @Nullable LocalVar as
  ) implements Pattern {
    public Ctor(@NotNull Pattern.Bind bind, @NotNull Var maybe) {
      this(bind.sourcePos(), bind.explicit(), new WithPos<>(bind.sourcePos(), maybe), ImmutableSeq.empty(), null);
    }
  }

  record BinOpSeq(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Pattern> seq,
    boolean explicit
  ) implements Pattern {}

  record ErrorPattern(
    @NotNull SourcePos sourcePos,
    @NotNull AyaDocile description,
    boolean explicit
  ) implements Pattern {
    public ErrorPattern(@NotNull SourcePos sourcePos, @NotNull Doc description) {
      this(sourcePos, options -> description, true);
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
  }
}
