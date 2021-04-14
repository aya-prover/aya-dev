// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.desugar;

import org.aya.concrete.*;
import org.aya.concrete.parse.BinOpParser;
import org.aya.concrete.visitor.ExprFixpoint;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

public final class Desugarer implements ExprFixpoint<Unit>, Stmt.Visitor<Unit, Unit> {
  public static final Desugarer INSTANCE = new Desugarer();

  private Desugarer() {
  }

  private void visitSignatured(@NotNull Signatured signatured) {
    signatured.telescope = signatured.telescope.map(p -> p.mapExpr(expr -> expr.accept(this, Unit.unit())));
  }

  private void visitDecl(@NotNull Decl decl) {
    visitSignatured(decl);
    decl.abuseBlock.forEach(stmt -> stmt.accept(this, Unit.unit()));
  }

  private Pattern.Clause visitClause(@NotNull Pattern.Clause c) {
    return new Pattern.Clause(c.sourcePos(), c.patterns(), c.expr().map(expr -> expr.accept(this, Unit.unit())));
  }

  @Override public Unit visitData(@NotNull Decl.DataDecl decl, Unit unit) {
    visitDecl(decl);
    decl.result = decl.result.accept(this, Unit.unit());
    decl.body.forEach(ctor -> {
      visitSignatured(ctor);
      ctor.clauses = ctor.clauses.map(this::visitClause);
    });
    return unit;
  }

  @Override public Unit visitStruct(@NotNull Decl.StructDecl decl, Unit unit) {
    visitDecl(decl);
    decl.result = decl.result.accept(this, Unit.unit());
    decl.fields.forEach(f -> {
      visitSignatured(f);
      f.result = f.result.accept(this, Unit.unit());
      f.clauses = f.clauses.map(this::visitClause);
      f.body = f.body.map(expr -> expr.accept(this, Unit.unit()));
    });
    return unit;
  }

  @Override public Unit visitFn(@NotNull Decl.FnDecl decl, Unit unit) {
    visitDecl(decl);
    decl.result = decl.result.accept(this, Unit.unit());
    decl.body = decl.body.map(
      expr -> expr.accept(this, Unit.unit()),
      clauses -> clauses.map(this::visitClause)
    );
    return unit;
  }

  @Override public Unit visitPrim(@NotNull Decl.PrimDecl decl, Unit unit) {
    visitDecl(decl);
    if (decl.result != null) decl.result = decl.result.accept(this, Unit.unit());
    return unit;
  }

  @Override public Unit visitImport(Stmt.@NotNull ImportStmt cmd, Unit unit) {
    return unit;
  }

  @Override public Unit visitOpen(Stmt.@NotNull OpenStmt cmd, Unit unit) {
    return unit;
  }

  @Override public Unit visitModule(Stmt.@NotNull ModuleStmt mod, Unit unit) {
    mod.contents().forEach(stmt -> stmt.accept(this, Unit.unit()));
    return unit;
  }

  @Override public @NotNull Expr visitBinOpSeq(@NotNull Expr.BinOpSeq binOpSeq, Unit unit) {
    var seq = binOpSeq.seq();
    assert seq.isNotEmpty() : binOpSeq.sourcePos().toString();
    return new BinOpParser(binOpSeq.seq().view())
      .build(binOpSeq.sourcePos())
      .accept(this, Unit.unit());
  }
}
