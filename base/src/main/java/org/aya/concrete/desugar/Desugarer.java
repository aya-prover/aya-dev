// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.desugar;

import org.aya.api.util.Arg;
import org.aya.concrete.*;
import org.aya.concrete.visitor.ExprFixpoint;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

public final class Desugarer implements ExprFixpoint<Unit>, Stmt.Visitor<Unit, Unit> {
  public static final Desugarer INSTANCE = new Desugarer();

  private Desugarer() {
  }

  private void visitSignatured(@NotNull Signatured signatured) {
    signatured.telescope = signatured.telescope.map(p -> p.mapExpr(Expr::desugar));
  }

  private void visitDecl(@NotNull Decl decl) {
    visitSignatured(decl);
    decl.abuseBlock.forEach(Stmt::desugar);
  }

  private Pattern.Clause visitClause(@NotNull Pattern.Clause c) {
    return new Pattern.Clause(c.sourcePos(), c.patterns(), c.expr().map(Expr::desugar));
  }

  @Override
  public Unit visitData(@NotNull Decl.DataDecl decl, Unit unit) {
    visitDecl(decl);
    decl.result = decl.result.desugar();
    decl.body.forEach(ctor -> {
      visitSignatured(ctor);
      ctor.clauses = ctor.clauses.map(this::visitClause);
    });
    return unit;
  }

  @Override
  public Unit visitStruct(@NotNull Decl.StructDecl decl, Unit unit) {
    visitDecl(decl);
    decl.result = decl.result.desugar();
    decl.fields.forEach(f -> {
      visitSignatured(f);
      f.result = f.result.desugar();
      f.clauses = f.clauses.map(this::visitClause);
      f.body = f.body.map(Expr::desugar);
    });
    return unit;
  }

  @Override
  public Unit visitFn(@NotNull Decl.FnDecl decl, Unit unit) {
    visitDecl(decl);
    decl.result = decl.result.desugar();
    decl.body = decl.body.map(
      Expr::desugar,
      clauses -> clauses.map(this::visitClause)
    );
    return unit;
  }

  @Override
  public Unit visitPrim(@NotNull Decl.PrimDecl decl, Unit unit) {
    visitDecl(decl);
    if (decl.result != null) decl.result = decl.result.desugar();
    return unit;
  }

  @Override
  public Unit visitImport(Stmt.@NotNull ImportStmt cmd, Unit unit) {
    return unit;
  }

  @Override
  public Unit visitOpen(Stmt.@NotNull OpenStmt cmd, Unit unit) {
    return unit;
  }

  @Override
  public Unit visitModule(Stmt.@NotNull ModuleStmt mod, Unit unit) {
    mod.contents().forEach(Stmt::desugar);
    return unit;
  }

  @Override
  public @NotNull Expr visitBinOpSeq(@NotNull Expr.BinOpSeq binOpSeq, Unit unit) {
    // TODO: implement
    return new Expr.AppExpr(
      binOpSeq.sourcePos(),
      binOpSeq.seq().first().expr().desugar(),
      binOpSeq.seq().view().drop(1)
        .map(e -> new Arg<>(e.expr().desugar(), e.explicit()))
        .toImmutableSeq()
    );
  }
}
