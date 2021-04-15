// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.desugar;

import org.aya.concrete.*;
import org.aya.concrete.visitor.ExprFixpoint;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

public final class Desugarer implements ExprFixpoint<BinOpSet>, Stmt.Visitor<BinOpSet, Unit> {
  public static final Desugarer INSTANCE = new Desugarer();

  private Desugarer() {
  }

  private void visitSignatured(@NotNull Signatured signatured, BinOpSet opSet) {
    signatured.telescope = signatured.telescope.map(p -> p.mapExpr(e -> e.desugar(opSet)));
  }

  private void visitDecl(@NotNull Decl decl, BinOpSet opSet) {
    visitSignatured(decl, opSet);
    decl.abuseBlock.forEach(s -> s.desugar(opSet));
  }

  private Pattern.Clause visitClause(@NotNull Pattern.Clause c, BinOpSet opSet) {
    return new Pattern.Clause(c.sourcePos(), c.patterns(), c.expr().map(e -> e.desugar(opSet)));
  }

  @Override
  public Unit visitData(@NotNull Decl.DataDecl decl, BinOpSet opSet) {
    visitDecl(decl, opSet);
    decl.result = decl.result.desugar(opSet);
    decl.body.forEach(ctor -> {
      visitSignatured(ctor, opSet);
      ctor.clauses = ctor.clauses.map(c -> visitClause(c, opSet));
    });
    return Unit.unit();
  }

  @Override
  public Unit visitStruct(@NotNull Decl.StructDecl decl, BinOpSet opSet) {
    visitDecl(decl, opSet);
    decl.result = decl.result.desugar(opSet);
    decl.fields.forEach(f -> {
      visitSignatured(f, opSet);
      f.result = f.result.desugar(opSet);
      f.clauses = f.clauses.map(c -> visitClause(c, opSet));
      f.body = f.body.map(e -> e.desugar(opSet));
    });
    return Unit.unit();
  }

  @Override
  public Unit visitFn(@NotNull Decl.FnDecl decl, BinOpSet opSet) {
    visitDecl(decl, opSet);
    decl.result = decl.result.desugar(opSet);
    decl.body = decl.body.map(
      e -> e.desugar(opSet),
      clauses -> clauses.map(c -> visitClause(c, opSet))
    );
    return Unit.unit();
  }

  @Override
  public Unit visitPrim(@NotNull Decl.PrimDecl decl, BinOpSet opSet) {
    visitDecl(decl, opSet);
    if (decl.result != null) decl.result = decl.result.desugar(opSet);
    return Unit.unit();
  }

  @Override
  public Unit visitImport(Stmt.@NotNull ImportStmt cmd, BinOpSet opSet) {
    return Unit.unit();
  }

  @Override
  public Unit visitOpen(Stmt.@NotNull OpenStmt cmd, BinOpSet opSet) {
    return Unit.unit();
  }

  @Override
  public Unit visitModule(Stmt.@NotNull ModuleStmt mod, BinOpSet opSet) {
    mod.contents().forEach(s -> s.desugar(opSet));
    return Unit.unit();
  }

  @Override public Unit visitBind(Stmt.@NotNull BindStmt bind, BinOpSet opSet) {
    return Unit.unit();
  }

  @Override
  public @NotNull Expr visitBinOpSeq(@NotNull Expr.BinOpSeq binOpSeq, BinOpSet opSet) {
    // TODO[kiva]: convert hole app?
    return new BinOpParser(opSet, binOpSeq.seq().view()
      .map(e -> new BinOpParser.Elem(e.expr().desugar(opSet), e.explicit()))
    ).build(binOpSeq.sourcePos());
  }
}
