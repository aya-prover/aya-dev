// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.desugar;

import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Unit;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.LevelGenVar;
import org.aya.api.util.Arg;
import org.aya.concrete.Expr;
import org.aya.concrete.desugar.error.LevelProblem;
import org.aya.concrete.visitor.StmtFixpoint;
import org.aya.generic.Level;
import org.aya.tyck.ExprTycker;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000, kiva
 */
public record Desugarer(@NotNull Reporter reporter, @NotNull BinOpSet opSet) implements StmtFixpoint<Unit> {
  @Override public @NotNull Expr visitApp(@NotNull Expr.AppExpr expr, Unit unit) {
    if (expr.function() instanceof Expr.RawUnivExpr univ) {
      try {
        return desugarUniv(expr, univ);
      } catch (DesugarInterruption e) {
        return new Expr.ErrorExpr(univ.sourcePos(), univ);
      }
    }
    return StmtFixpoint.super.visitApp(expr, unit);
  }

  @Override public @NotNull Expr visitRawUniv(@NotNull Expr.RawUnivExpr expr, Unit unit) {
    try {
      return desugarUniv(new Expr.AppExpr(expr.sourcePos(), expr, ImmutableSeq.empty()), expr);
    } catch (DesugarInterruption e) {
      return new Expr.ErrorExpr(expr.sourcePos(), expr);
    }
  }

  @NotNull private Expr desugarUniv(Expr.@NotNull AppExpr expr, Expr.RawUnivExpr univ) throws DesugarInterruption {
    var uLevel = univ.uLevel();
    var hLevel = univ.hLevel();
    var pos = univ.sourcePos();
    if (hLevel == Expr.RawUnivExpr.NEEDED) {
      if (uLevel == Expr.RawUnivExpr.NEEDED) {
        var args = expectArgs(expr, 2, false);
        var h = levelVar(LevelGenVar.Kind.Homotopy, args.get(0).term().expr());
        var u = levelVar(LevelGenVar.Kind.Universe, args.get(1).term().expr());
        return new Expr.UnivExpr(pos, u, h);
      } else if (uLevel >= 0) {
        var args = expectArgs(expr, 1, false);
        var h = levelVar(LevelGenVar.Kind.Homotopy, args.get(0).term().expr());
        return new Expr.UnivExpr(pos, new Level.Constant<>(uLevel), h);
      } else if (uLevel == Expr.RawUnivExpr.POLYMORPHIC) {
        var args = expectArgs(expr, 1, false);
        var h = levelVar(LevelGenVar.Kind.Homotopy, args.get(0).term().expr());
        return new Expr.UnivExpr(pos, new Level.Polymorphic(0), h);
      } else throw new IllegalStateException("Invalid uLevel: " + uLevel);
    } else if (hLevel >= 0) {
      return withHLevel(expr, uLevel, pos, new Level.Constant<>(hLevel));
    } else if (hLevel == Expr.RawUnivExpr.POLYMORPHIC) {
      if (uLevel == Expr.RawUnivExpr.POLYMORPHIC) {
        var args = expectArgs(expr, 2, true);
        if (args.isNotEmpty()) {
          var h = levelVar(LevelGenVar.Kind.Homotopy, args.get(0).term().expr());
          var u = levelVar(LevelGenVar.Kind.Universe, args.get(1).term().expr());
          return new Expr.UnivExpr(pos, u, h);
        } else return new Expr.UnivExpr(pos, new Level.Polymorphic(0), new Level.Polymorphic(0));
      } else return withHLevel(expr, uLevel, pos, new Level.Polymorphic(0));
    } else if (hLevel == Expr.RawUnivExpr.INFINITY) {
      return withHLevel(expr, uLevel, pos, new Level.Infinity<>());
    } else throw new IllegalStateException("Invalid hLevel: " + hLevel);
  }

  @Contract("_, _, _, _ -> new")
  private Expr withHLevel(Expr.@NotNull AppExpr expr, int uLevel, @NotNull SourcePos pos, Level<LevelGenVar> h)
    throws DesugarInterruption {
    if (uLevel >= 0) {
      expectArgs(expr, 0, false);
      return new Expr.UnivExpr(pos, new Level.Constant<>(uLevel), h);
    } else if (uLevel == Expr.RawUnivExpr.NEEDED) {
      var args = expectArgs(expr, 1, false);
      var u = levelVar(LevelGenVar.Kind.Universe, args.first().term().expr());
      return new Expr.UnivExpr(pos, u, h);
    } else if (uLevel == Expr.RawUnivExpr.POLYMORPHIC) {
      var args = expectArgs(expr, 1, true);
      return new Expr.UnivExpr(pos, args.isEmpty() ? new Level.Polymorphic(0)
        : levelVar(LevelGenVar.Kind.Universe, args.first().term().expr()), h);
    } else if (uLevel == Expr.RawUnivExpr.INFINITY) {
      expectArgs(expr, 0, false);
      return new Expr.UnivExpr(pos, new Level.Infinity<>(), h);
    } else throw new IllegalStateException("Invalid uLevel: " + uLevel);
  }

  private @NotNull ImmutableSeq<@NotNull Arg<Expr.NamedArg>>
  expectArgs(Expr.@NotNull AppExpr expr, int n, boolean canEmpty) throws DesugarInterruption {
    var args = expr.arguments();
    if (canEmpty && args.isEmpty()) return args;
    if (!args.sizeEquals(n)) {
      reporter.report(new LevelProblem.BadTypeExpr(expr, n));
      throw new DesugarInterruption();
    }
    return args;
  }

  public static class DesugarInterruption extends Exception {
  }

  private @NotNull Level<LevelGenVar> levelVar(LevelGenVar.Kind kind, @NotNull Expr expr) throws DesugarInterruption {
    if (expr instanceof Expr.LitIntExpr uLit) {
      return new Level.Constant<>(uLit.integer());
    } else if (expr instanceof Expr.LSucExpr uSuc) {
      return levelVar(kind, uSuc.expr()).lift(1);
    } else if (expr instanceof Expr.LMaxExpr uMax) {
      return new Level.Maximum(uMax.levels().mapChecked(x -> levelVar(kind, x)));
    } else if (expr instanceof Expr.RefExpr ref && ref.resolvedVar() instanceof LevelGenVar lv) {
      if (lv.kind() != kind) {
        reporter.report(new LevelProblem.BadLevelKind(ref, lv.kind()));
        throw new DesugarInterruption();
      } else return new Level.Reference<>(lv);
    } else {
      reporter.report(new LevelProblem.BadLevelExpr(expr));
      throw new ExprTycker.TyckerException();
    }
  }

  @Override public @NotNull Expr visitBinOpSeq(@NotNull Expr.BinOpSeq binOpSeq, Unit unit) {
    var seq = binOpSeq.seq();
    assert seq.isNotEmpty() : binOpSeq.sourcePos().toString();
    return new BinOpParser(opSet, seq.view())
      .build(binOpSeq.sourcePos())
      .accept(this, Unit.unit());
  }
}
