// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.desugar;

import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Unit;
import org.aya.api.error.Reporter;
import org.aya.api.ref.LevelGenVar;
import org.aya.concrete.Expr;
import org.aya.concrete.desugar.error.LevelProblem;
import org.aya.concrete.visitor.StmtFixpoint;
import org.aya.generic.Level;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000, kiva
 */
public record Desugarer(@NotNull Reporter reporter, @NotNull BinOpSet opSet) implements StmtFixpoint<Unit> {
  @Override public @NotNull Expr visitApp(@NotNull Expr.AppExpr expr, Unit unit) {
    if (expr.function() instanceof Expr.RawUnivExpr univ) return desugarUniv(expr, univ);
    return StmtFixpoint.super.visitApp(expr, unit);
  }

  @Override public @NotNull Expr visitRawUniv(@NotNull Expr.RawUnivExpr expr, Unit unit) {
    return desugarUniv(new Expr.AppExpr(expr.sourcePos(), expr, ImmutableSeq.empty()), expr);
  }

  @NotNull private Expr desugarUniv(Expr.@NotNull AppExpr expr, Expr.RawUnivExpr univ) {
    var pos = univ.sourcePos();
    var args = expr.arguments();
    if (args.isEmpty()) return new Expr.UnivExpr(pos, new Level.Polymorphic(0));
    if (!args.sizeEquals(1)) {
      reporter.report(new LevelProblem.BadTypeExpr(expr, 1));
      return new Expr.ErrorExpr(expr.sourcePos(), expr);
    }
    try {
      return new Expr.UnivExpr(pos, levelVar(args.get(0).term().expr()));
    } catch (DesugarInterruption e) {
      return new Expr.ErrorExpr(expr.sourcePos(), expr);
    }
  }

  public static class DesugarInterruption extends Exception {
  }

  private @NotNull Level<LevelGenVar> levelVar(@NotNull Expr expr) throws DesugarInterruption {
    //noinspection ConstantConditions
    var level = switch (expr) {
      case Expr.LMaxExpr uMax -> new Level.Maximum(uMax.levels().mapChecked(this::levelVar));
      case Expr.LSucExpr uSuc -> levelVar(uSuc.expr()).lift(1);
      case Expr.LitIntExpr uLit -> new Level.Constant<LevelGenVar>(uLit.integer());
      case Expr.RefExpr ref && ref.resolvedVar() instanceof LevelGenVar lv -> new Level.Reference<>(lv);
      default -> {
        reporter.report(new LevelProblem.BadLevelExpr(expr));
        yield null;
      }
    };
    //noinspection ConstantConditions
    if (level == null) throw new DesugarInterruption();
    else return level;
  }

  @Override public @NotNull Expr visitBinOpSeq(@NotNull Expr.BinOpSeq binOpSeq, Unit unit) {
    var seq = binOpSeq.seq();
    assert seq.isNotEmpty() : binOpSeq.sourcePos().toString();
    return new BinOpParser(opSet, seq.view())
      .build(binOpSeq.sourcePos())
      .accept(this, Unit.unit());
  }
}
