// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp.actions;

import kala.tuple.Unit;
import org.aya.api.util.NormalizeMode;
import org.aya.api.util.WithPos;
import org.aya.concrete.Expr;
import org.aya.core.term.Term;
import org.aya.lsp.models.ComputeTypeResult;
import org.aya.lsp.server.AyaService;
import org.aya.lsp.utils.XY;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public final class ComputeTerm implements SyntaxNodeAction {
  private @Nullable WithPos<Term> result = null;
  private final @NotNull AyaService.AyaFile loadedFile;
  private final @NotNull Function<Term, Term> map;

  private ComputeTerm(AyaService.@NotNull AyaFile loadedFile, @NotNull Function<Term, Term> map) {
    this.loadedFile = loadedFile;
    this.map = map;
  }

  public static @NotNull ComputeTypeResult computeType(@NotNull ComputeTypeResult.Params params, @NotNull AyaService.AyaFile loadedFile) {
    return new ComputeTerm(loadedFile, Term::computeType).invoke(params);
  }

  public static @NotNull ComputeTypeResult computeNF(@NotNull ComputeTypeResult.Params params, @NotNull AyaService.AyaFile loadedFile) {
    return new ComputeTerm(loadedFile, term -> term.normalize(NormalizeMode.NF)).invoke(params);
  }

  private @NotNull ComputeTypeResult invoke(ComputeTypeResult.Params params) {
    visitAll(loadedFile.concrete(), new XY(params.position));
    return result == null ? ComputeTypeResult.bad(params) : ComputeTypeResult.good(params, result);
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
      if (core != null) result = new WithPos<>(sourcePos, map.apply(core));
    }
  }
}
