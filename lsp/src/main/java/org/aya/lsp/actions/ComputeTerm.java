// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.tuple.Unit;
import org.aya.api.util.NormalizeMode;
import org.aya.cli.library.source.LibrarySource;
import org.aya.concrete.Expr;
import org.aya.core.term.Term;
import org.aya.lsp.models.ComputeTermResult;
import org.aya.lsp.utils.XY;
import org.aya.tyck.ExprTycker;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public final class ComputeTerm implements SyntaxNodeAction {
  private @Nullable WithPos<Term> result = null;
  private final @NotNull LibrarySource loadedFile;
  private final @NotNull Kind kind;

  public enum Kind {
    Type(ExprTycker.Result::type),
    Id(ExprTycker.Result::wellTyped),
    Nf(term -> term.wellTyped().normalize(null, NormalizeMode.NF)),
    Whnf(term -> term.wellTyped().normalize(null, NormalizeMode.WHNF)),
    ;
    private final Function<ExprTycker.Result, Term> map;

    Kind(Function<ExprTycker.Result, Term> map) {
      this.map = map;
    }
  }

  public ComputeTerm(@NotNull LibrarySource loadedFile, @NotNull Kind kind) {
    this.loadedFile = loadedFile;
    this.kind = kind;
  }

  public @NotNull ComputeTermResult invoke(ComputeTermResult.Params params) {
    var program = loadedFile.program().value;
    if (program == null) return ComputeTermResult.bad(params);
    visitAll(program, new XY(params.position));
    return result == null ? ComputeTermResult.bad(params) : ComputeTermResult.good(params, result);
  }

  @Override public @NotNull Unit visitRef(@NotNull Expr.RefExpr expr, XY xy) {
    check(xy, expr);
    return Unit.unit();
  }

  @Override public @NotNull Unit visitProj(@NotNull Expr.ProjExpr expr, XY xy) {
    check(xy, expr);
    return SyntaxNodeAction.super.visitProj(expr, xy);
  }

  private <T extends Expr.WithTerm> void check(@NotNull XY xy, @NotNull T cored) {
    var sourcePos = cored.sourcePos();
    if (xy.inside(sourcePos)) {
      var core = cored.core();
      if (core != null) result = new WithPos<>(sourcePos, kind.map.apply(core));
    }
  }
}
