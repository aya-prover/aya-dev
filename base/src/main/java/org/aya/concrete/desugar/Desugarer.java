// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.desugar;

import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
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
import org.glavo.kala.collection.immutable.ImmutableSeq;
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
        var h = levelVar(args.get(0), LevelVar.Kind.Homotopy);
        var u = levelVar(args.get(1), LevelVar.Kind.Universe);
        return new Expr.UnivExpr(pos, u, h);
      } else if (uLevel >= 0) {
        var args = expectArgs(expr, 1);
        var h = levelVar(args.get(0), LevelVar.Kind.Homotopy);
        return new Expr.UnivExpr(pos, Level.Constant.make(uLevel, LevelVar.Kind.Universe), h);
      } else if (uLevel == Expr.RawUnivExpr.POLYMORPHIC) {
        var args = expectArgs(expr, 1);
        var h = levelVar(args.get(0), LevelVar.Kind.Homotopy);
        return new Expr.UnivExpr(pos, Level.Polymorphic.make(0, LevelVar.Kind.Universe), h);
      } else throw new IllegalStateException("Invalid uLevel: " + uLevel);
    } else if (hLevel >= 0) {
      return withHomotopyLevel(expr, uLevel, pos, Level.Constant.make(hLevel, LevelVar.Kind.Homotopy));
    } else if (hLevel == Expr.RawUnivExpr.POLYMORPHIC) {
      return withHomotopyLevel(expr, uLevel, pos, Level.Polymorphic.make(0, LevelVar.Kind.Homotopy));
    } else throw new IllegalStateException("Invalid hLevel: " + hLevel);
  }

  @Contract("_, _, _, _ -> new") private Expr.@NotNull UnivExpr withHomotopyLevel(
    Expr.@NotNull AppExpr expr, int uLevel, @NotNull SourcePos pos, LevelVar<Level> h
  ) {
    if (uLevel >= 0) {
      expectArgs(expr, 0);
      var u = Level.Constant.make(uLevel, LevelVar.Kind.Universe);
      return new Expr.UnivExpr(pos, u, h);
    } else if (uLevel == Expr.RawUnivExpr.NEEDED) {
      var args = expectArgs(expr, 1);
      var u = levelVar(args.get(0), LevelVar.Kind.Universe);
      return new Expr.UnivExpr(pos, u, h);
    } else if (uLevel == Expr.RawUnivExpr.POLYMORPHIC) {
      expectArgs(expr, 0);
      return new Expr.UnivExpr(pos, Level.Polymorphic.make(0, LevelVar.Kind.Universe), h);
    } else throw new IllegalStateException("Invalid uLevel: " + uLevel);
  }

  @NotNull private ImmutableSeq<@NotNull Arg<Expr>> expectArgs(Expr.@NotNull AppExpr expr, int n) {
    var args = expr.arguments();
    if (!args.sizeEquals(n)) {
      reporter.report(new WrongLevelError(expr, n));
      throw new DesugarInterruptedException();
    }
    return args;
  }

  private @NotNull LevelVar<Level> levelVar(Arg<Expr> uArg, LevelVar.Kind kind) {
    var u = new LevelVar<Level>(Constants.ANONYMOUS_PREFIX, kind);
    if (uArg.term() instanceof Expr.LitIntExpr uLit) u.level().value = new Level.Constant(uLit.integer());
    else if (uArg.term() instanceof Expr.RefExpr ref && ref.resolvedVar() instanceof LevelVar<?> lv) {
      u.level().value = new Level.Reference(Level.narrow(lv), 0);
    } else {
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
