// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action;

import org.aya.cli.library.source.LibrarySource;
import org.aya.concrete.Expr;
import org.aya.core.def.PrimDef;
import org.aya.core.term.Term;
import org.aya.generic.util.NormalizeMode;
import org.aya.ide.syntax.SyntaxNodeAction;
import org.aya.ide.util.XY;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.TyckState;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

public final class ComputeTerm implements SyntaxNodeAction.Cursor {
  public @Nullable WithPos<Term> result = null;
  private final @NotNull LibrarySource source;
  private final @NotNull Kind kind;
  private final @NotNull PrimDef.Factory primFactory;
  private final @NotNull XY location;

  @Override public @NotNull XY location() {
    return location;
  }

  public record Kind(@NotNull BiFunction<PrimDef.Factory, ExprTycker.Result, Term> map) {
    public static @NotNull Kind type() {
      return new Kind((fac, term) -> term.type());
    }

    public static @NotNull Kind id() {
      return new Kind((fac, term) -> term.wellTyped());
    }

    public static @NotNull Kind nf() {
      return new Kind((fac, term) -> term.wellTyped().normalize(new TyckState(fac), NormalizeMode.NF));
    }

    public static @NotNull Kind whnf() {
      return new Kind((fac, term) -> term.wellTyped().normalize(new TyckState(fac), NormalizeMode.WHNF));
    }
  }

  public ComputeTerm(@NotNull LibrarySource source, @NotNull Kind kind, @NotNull PrimDef.Factory primFactory, @NotNull XY location) {
    this.source = source;
    this.kind = kind;
    this.primFactory = primFactory;
    this.location = location;
  }

  @Override public @NotNull Expr pre(@NotNull Expr expr) {
    if (expr instanceof Expr.WithTerm withTerm) {
      var core = withTerm.core();
      if (core != null) result = new WithPos<>(withTerm.sourcePos(), kind.map.apply(primFactory, core));
    }
    return Cursor.super.pre(expr);
  }
}
