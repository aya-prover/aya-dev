// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete.stmt.decl;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.error.PosedConsumer;
import org.aya.util.error.PosedUnaryOperator;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public sealed interface FnBody {
  FnBody map(@NotNull PosedUnaryOperator<Expr> f, @NotNull UnaryOperator<Pattern.Clause> g);
  void forEach(@NotNull PosedConsumer<Expr> f, @NotNull Consumer<Pattern.Clause> g);
  record ExprBody(@NotNull WithPos<Expr> expr) implements FnBody {
    @Override public ExprBody map(@NotNull PosedUnaryOperator<Expr> f, @NotNull UnaryOperator<Pattern.Clause> g) {
      return new ExprBody(expr.descent(f));
    }
    @Override public void forEach(@NotNull PosedConsumer<Expr> f, @NotNull Consumer<Pattern.Clause> g) {
      f.accept(expr);
    }
  }

  record BlockBody(@NotNull MatchBody<Pattern.Clause> inner) implements FnBody {
    public BlockBody(
      @NotNull ImmutableSeq<Pattern.Clause> clauses,
      @NotNull ImmutableSeq<WithPos<String>> rawElims
    ) {
      this(new MatchBody<>(clauses, rawElims));
    }

    @Contract(pure = true) public @NotNull ImmutableSeq<Pattern.Clause> clauses() {
      return inner.clauses;
    }

    @Contract(pure = true) public @Nullable ImmutableSeq<LocalVar> elims() {
      return inner.elims();
    }
    public @NotNull ImmutableSeq<WithPos<String>> rawElims() { return inner.rawElims; }

    public @NotNull BlockBody map(@NotNull UnaryOperator<Pattern.Clause> f) {
      return new BlockBody(inner.descent(f));
    }

    @Override public BlockBody map(@NotNull PosedUnaryOperator<Expr> f, @NotNull UnaryOperator<Pattern.Clause> g) {
      return new BlockBody(inner.descent(g));
    }

    @Override public void forEach(@NotNull PosedConsumer<Expr> f, @NotNull Consumer<Pattern.Clause> g) {
      clauses().forEach(g);
    }
  }
}
