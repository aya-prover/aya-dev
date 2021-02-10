// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.desugar;

import org.glavo.kala.Unit;
import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.Decl;
import org.mzi.concrete.Expr;
import org.mzi.concrete.Stmt;
import org.mzi.concrete.visitor.ExprFixpoint;

public final class Desugarer implements ExprFixpoint<Unit>, Stmt.Visitor<Unit, Unit> {
  public static final Desugarer INSTANCE = new Desugarer();

  private @NotNull Expr makeNestingLam(Expr.@NotNull TelescopicLamExpr expr, int pos) {
    if (pos == expr.params().size()) return expr.body().accept(this, Unit.unit());
    return new Expr.LamExpr(expr.sourcePos(), expr.params().get(pos), makeNestingLam(expr, pos + 1));
  }

  @Override
  public @NotNull Expr visitTelescopicLam(Expr.@NotNull TelescopicLamExpr expr, Unit u) {
    return makeNestingLam(expr, 0);
  }

  private @NotNull Expr makeNestingPi(Expr.@NotNull TelescopicPiExpr expr, int pos) {
    if (pos == expr.params().size()) return expr.last().accept(this, Unit.unit());
    return new Expr.PiExpr(expr.sourcePos(), expr.co(), expr.params().get(pos), makeNestingPi(expr, pos + 1));
  }

  @Override
  public @NotNull Expr visitTelescopicPi(Expr.@NotNull TelescopicPiExpr expr, Unit u) {
    return makeNestingPi(expr, 0);
  }

  private Desugarer() {}

  @Override public Unit visitCmd(Stmt.@NotNull CmdStmt cmd, Unit unit) {
    return unit;
  }

  @Override public Unit visitModule(Stmt.@NotNull ModuleStmt mod, Unit unit) {
    mod.contents().forEach(stmt -> stmt.accept(this, unit));
    return unit;
  }

  @Override public Unit visitDataDecl(Decl.@NotNull DataDecl decl, Unit unit) {
    decl.abuseBlock.forEach(stmt -> stmt.accept(this, unit));
    decl.result = decl.result.desugar();
    // TODO this type is not yet finished
    return unit;
  }

  @Override public Unit visitFnDecl(Decl.@NotNull FnDecl decl, Unit unit) {
    decl.abuseBlock.forEach(stmt -> stmt.accept(this, unit));
    decl.result = decl.result.desugar();
    decl.telescope = decl.telescope.map(p -> p.mapExpr(Expr::desugar));
    decl.body = decl.body.desugar();
    return unit;
  }
}
