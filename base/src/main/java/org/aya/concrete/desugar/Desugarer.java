// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.desugar;

import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.api.util.Arg;
import org.aya.concrete.Expr;
import org.aya.concrete.LevelPrevar;
import org.aya.concrete.desugar.error.DesugarInterruptedException;
import org.aya.concrete.desugar.error.LevelProblem;
import org.aya.concrete.visitor.StmtFixpoint;
import org.aya.tyck.ExprTycker;
import org.aya.util.Constants;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.control.Either;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000, kiva
 */
public record Desugarer(@NotNull Reporter reporter, @NotNull BinOpSet opSet) implements StmtFixpoint<Unit> {
  @Override public @NotNull Expr visitApp(@NotNull Expr.AppExpr expr, Unit unit) {
    if (expr.function() instanceof Expr.RawUnivExpr univ) return desugarUniv(expr, univ);
    return StmtFixpoint.super.visitApp(expr, unit);
  }

  @NotNull private Expr.UnivExpr desugarUniv(Expr.@NotNull AppExpr expr, Expr.RawUnivExpr univ) {
    var uLevel = univ.uLevel();
    var hLevel = univ.hLevel();
    var pos = univ.sourcePos();
    if (hLevel == Expr.RawUnivExpr.NEEDED) {
      if (uLevel == Expr.RawUnivExpr.NEEDED) {
        var args = expectArgs(expr, 2);
        var h = levelVar(args.get(0), LevelPrevar.Kind.Homotopy);
        var u = levelVar(args.get(1), LevelPrevar.Kind.Universe);
        return new Expr.UnivExpr(pos, u, h);
      } else if (uLevel >= 0) {
        var args = expectArgs(expr, 1);
        var h = levelVar(args.get(0), LevelPrevar.Kind.Homotopy);
        return new Expr.UnivExpr(pos, LevelPrevar.make(uLevel, LevelPrevar.Kind.Universe), h);
      } else if (uLevel == Expr.RawUnivExpr.POLYMORPHIC) {
        var args = expectArgs(expr, 1);
        var h = levelVar(args.get(0), LevelPrevar.Kind.Homotopy);
        return new Expr.UnivExpr(pos, new LevelPrevar(Constants.ANONYMOUS_PREFIX, LevelPrevar.Kind.Universe), h);
      } else throw new IllegalStateException("Invalid uLevel: " + uLevel);
    } else if (hLevel >= 0) {
      return withHomotopyLevel(expr, uLevel, pos, LevelPrevar.make(hLevel, LevelPrevar.Kind.Homotopy));
    } else if (hLevel == Expr.RawUnivExpr.POLYMORPHIC) {
      return withHomotopyLevel(expr, uLevel, pos, new LevelPrevar(Constants.ANONYMOUS_PREFIX, LevelPrevar.Kind.Homotopy));
    } else if (hLevel == Expr.RawUnivExpr.INFINITY) {
      return withHomotopyLevel(expr, uLevel, pos, LevelPrevar.make(-1, LevelPrevar.Kind.Homotopy));
    } else throw new IllegalStateException("Invalid hLevel: " + hLevel);
  }

  @Contract("_, _, _, _ -> new") private Expr.@NotNull UnivExpr withHomotopyLevel(
    Expr.@NotNull AppExpr expr, int uLevel, @NotNull SourcePos pos, LevelPrevar h
  ) {
    if (uLevel >= 0) {
      expectArgs(expr, 0);
      var u = LevelPrevar.make(uLevel, LevelPrevar.Kind.Universe);
      return new Expr.UnivExpr(pos, u, h);
    } else if (uLevel == Expr.RawUnivExpr.NEEDED) {
      var args = expectArgs(expr, 1);
      var u = levelVar(args.get(0), LevelPrevar.Kind.Universe);
      return new Expr.UnivExpr(pos, u, h);
    } else if (uLevel == Expr.RawUnivExpr.POLYMORPHIC) {
      expectArgs(expr, 0);
      return new Expr.UnivExpr(pos, new LevelPrevar(Constants.ANONYMOUS_PREFIX, LevelPrevar.Kind.Universe), h);
    } else if (uLevel == Expr.RawUnivExpr.INFINITY) {
      expectArgs(expr, 0);
      return new Expr.UnivExpr(pos, LevelPrevar.make(-1, LevelPrevar.Kind.Universe), h);
    } else throw new IllegalStateException("Invalid uLevel: " + uLevel);
  }

  @NotNull private ImmutableSeq<@NotNull Arg<Expr>> expectArgs(Expr.@NotNull AppExpr expr, int n) {
    var args = expr.arguments();
    if (!args.sizeEquals(n)) {
      reporter.report(new LevelProblem.BadTypeExpr(expr, n));
      throw new DesugarInterruptedException();
    }
    return args;
  }

  private @NotNull LevelPrevar levelVar(Arg<Expr> uArg, LevelPrevar.Kind kind) {
    if (uArg.term() instanceof Expr.LitIntExpr uLit) {
      return LevelPrevar.make(uLit.integer(), kind);
    } else if (uArg.term() instanceof Expr.RefExpr ref && ref.resolvedVar() instanceof LevelPrevar lv) {
      if (lv.kind() != kind) {
        reporter.report(new LevelProblem.BadLevelKind(ref, lv.kind()));
        throw new DesugarInterruptedException();
      }
      return new LevelPrevar(Constants.ANONYMOUS_PREFIX, kind, Either.right(lv));
    } else {
      reporter.report(new LevelProblem.BadLevelExpr(uArg.term()));
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
