// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action;

import org.aya.cli.library.source.LibrarySource;
import org.aya.ide.syntax.SyntaxNodeAction;
import org.aya.ide.util.XY;
import org.aya.normalize.Normalizer;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.literate.CodeOptions;
import org.aya.tyck.TyckState;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

public final class ComputeType implements SyntaxNodeAction.Cursor {
  public @Nullable WithPos<Term> result = null;
  private final @NotNull LibrarySource source;
  private final @NotNull Kind kind;
  private final @NotNull Normalizer normalizer;
  private final @NotNull XY location;

  @Override public @NotNull XY location() { return location; }

  public record Kind(@NotNull BiFunction<Normalizer, Term, Term> map) {
    public static @NotNull Kind type() { return new Kind((_, term) -> term); }
    public static @NotNull Kind nf() {
      return new Kind((fac, term) -> fac.normalize(term, CodeOptions.NormalizeMode.FULL));
    }
    public static @NotNull Kind whnf() {
      return new Kind((fac, term) -> fac.normalize(term, CodeOptions.NormalizeMode.HEAD));
    }
  }

  public ComputeType(@NotNull LibrarySource source, @NotNull Kind kind, @NotNull TyckState state, @NotNull XY location) {
    this.source = source;
    this.kind = kind;
    normalizer = new Normalizer(state);
    this.location = location;
  }

  public void visit(@NotNull SourcePos pos, @NotNull Expr expr) {
    expr.forEach((subPos, subExpr) -> {
      if (expr instanceof Expr.WithTerm withTerm) {
        var core = withTerm.coreType();
        if (core != null) result = new WithPos<>(pos, kind.map.apply(normalizer, core));
      }
      visit(subPos, subExpr);
    });
  }
}
