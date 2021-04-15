// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.desugar;

import org.aya.api.error.Reporter;
import org.aya.api.ref.LevelVar;
import org.aya.api.util.Arg;
import org.aya.concrete.Expr;
import org.aya.concrete.desugar.error.BadLevelError;
import org.aya.concrete.desugar.error.DesugarInterruptedException;
import org.aya.concrete.desugar.error.WrongLevelError;
import org.aya.concrete.visitor.StmtFixpoint;
import org.aya.core.sort.Level;
import org.aya.tyck.ExprTycker;
import org.aya.util.Constants;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

public record Desugarer(@NotNull Reporter reporter, @NotNull BinOpSet opSet) implements StmtFixpoint<Unit> {
  @Override public @NotNull Expr visitApp(@NotNull Expr.AppExpr expr, Unit unit) {
    if (expr.function() instanceof Expr.RawUnivExpr univ) {
      var uLevel = univ.uLevel();
      var hLevel = univ.hLevel();
      if (uLevel == Expr.RawUnivExpr.NEEDED) {
        if (hLevel == Expr.RawUnivExpr.NEEDED) {
          var args = expr.arguments();
          if (!args.sizeEquals(2)) {
            reporter.report(new WrongLevelError(expr, 2));
            throw new DesugarInterruptedException();
          }
          var h = getLevelLevelVar(args.get(0), LevelVar.Kind.Homotopy);
          var u = getLevelLevelVar(args.get(1), LevelVar.Kind.Universe);
          return new Expr.UnivExpr(univ.sourcePos(), u, h);
        }
      }
    }
    return StmtFixpoint.super.visitApp(expr, unit);
  }

  private @NotNull LevelVar<Level> getLevelLevelVar(Arg<Expr> uArg, LevelVar.Kind kind) {
    var u = new LevelVar<Level>(Constants.ANONYMOUS_PREFIX, kind);
    if (uArg.term() instanceof Expr.LitIntExpr uLit) u.level().value = new Level.Constant(uLit.integer());
    else {
      // TODO[ice]: universe vars
      reporter.report(new BadLevelError(uArg.term()));
      throw new ExprTycker.TyckerException();
    }
    return u;
  }

  @Override public @NotNull Expr visitBinOpSeq(@NotNull Expr.BinOpSeq binOpSeq, Unit unit) {
    var seq = binOpSeq.seq();
    assert seq.isNotEmpty() : binOpSeq.sourcePos().toString();
    return new BinOpParser(opSet, seq.view())
      .build(binOpSeq.sourcePos())
      .accept(this, Unit.unit());
  }
}
