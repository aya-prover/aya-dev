// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp.actions;

import kala.tuple.Unit;
import org.aya.api.util.WithPos;
import org.aya.concrete.Expr;
import org.aya.concrete.visitor.StmtConsumer;
import org.aya.core.term.Term;
import org.aya.lsp.models.ComputeTypeResult;
import org.aya.lsp.server.AyaService;
import org.aya.lsp.utils.XY;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ComputeType implements StmtConsumer<XY> {
  public @Nullable WithPos<Term> types = null;
  public final @NotNull AyaService.AyaFile loadedFile;

  public static ComputeTypeResult invoke(@NotNull ComputeTypeResult.Params params, @NotNull AyaService.AyaFile loadedFile) {
    var computer = new ComputeType(loadedFile);
    computer.visitAll(loadedFile.concrete(), new XY(params.position));
    return computer.types == null ? ComputeTypeResult.bad(params) : ComputeTypeResult.good(params, computer.types);
  }

  public ComputeType(AyaService.@NotNull AyaFile loadedFile) {
    this.loadedFile = loadedFile;
  }

  @Override public @NotNull Unit visitRef(@NotNull Expr.RefExpr expr, XY xy) {
    check(xy, expr);
    return Unit.unit();
  }

  @Override public @NotNull Unit visitProj(@NotNull Expr.ProjExpr expr, XY xy) {
    check(xy, expr);
    return StmtConsumer.super.visitProj(expr, xy);
  }

  private <T extends Expr.WithTerm> void check(@NotNull XY xy, @NotNull T cored) {
    var sourcePos = cored.sourcePos();
    if (xy.inside(sourcePos)) {
      var core = cored.core();
      if (core != null) types = new WithPos<>(sourcePos, core.computeType());
    }
  }
}
