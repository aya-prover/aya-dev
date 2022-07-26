// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import org.aya.cli.library.source.LibrarySource;
import org.aya.concrete.Expr;
import org.aya.core.def.PrimDef;
import org.aya.core.term.Term;
import org.aya.generic.util.NormalizeMode;
import org.aya.lsp.models.ComputeTermResult;
import org.aya.lsp.utils.XY;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.TyckState;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

public final class ComputeTerm implements SyntaxNodeAction.Cursor {
  private @Nullable WithPos<Term> result = null;
  private final @NotNull LibrarySource source;
  private final @NotNull Kind kind;
  private final @NotNull PrimDef.Factory primFactory;

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
      return new Kind((fac, term)  -> term.wellTyped().normalize(new TyckState(fac), NormalizeMode.WHNF));
    }
  }

  public ComputeTerm(@NotNull LibrarySource source, @NotNull Kind kind, @NotNull PrimDef.Factory primFactory) {
    this.source = source;
    this.kind = kind;
    this.primFactory = primFactory;
  }

  public @NotNull ComputeTermResult invoke(ComputeTermResult.Params params) {
    var program = source.program().get();
    if (program == null) return ComputeTermResult.bad(params);
    visitAll(program, new XY(params.position));
    return result == null ? ComputeTermResult.bad(params) : ComputeTermResult.good(params, result);
  }

  @Override public @NotNull Expr visitExpr(@NotNull Expr expr, XY xy) {
    if (expr instanceof Expr.WithTerm withTerm) {
      var sourcePos = withTerm.sourcePos();
      if (xy.inside(sourcePos)) {
        var core = withTerm.core();
        if (core != null) result = new WithPos<>(sourcePos, kind.map.apply(primFactory, core));
      }
    }
    return Cursor.super.visitExpr(expr, xy);
  }
}
