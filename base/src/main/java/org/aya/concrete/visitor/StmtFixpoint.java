// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.visitor;

import kala.tuple.Unit;
import org.aya.concrete.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public interface StmtFixpoint<P> extends ExprFixpoint<P>, Stmt.Visitor<P, Unit>, Signatured.Visitor<P, Unit>, Decl.Visitor<P, Unit> {
  default void visitSignatured(@NotNull Signatured signatured, P pp) {
    signatured.telescope = signatured.telescope.map(p -> p.mapExpr(expr -> expr.accept(this, pp)));
  }

  default void visitDecl(@NotNull Decl decl, P pp) {
    visitSignatured(decl, pp);
    decl.abuseBlock.forEach(stmt -> stmt.accept(this, pp));
  }

  default @NotNull Pattern.Clause visitClause(@NotNull Pattern.Clause c, P pp) {
    return new Pattern.Clause(c.sourcePos, c.patterns, c.expr.map(expr -> expr.accept(this, pp)));
  }

  @Override default Unit visitData(@NotNull Decl.DataDecl decl, P p) {
    visitDecl(decl, p);
    decl.result = decl.result.accept(this, p);
    decl.body.forEach(ctor -> ctor.accept(this, p));
    return Unit.unit();
  }

  @Override default Unit visitStruct(@NotNull Decl.StructDecl decl, P p) {
    visitDecl(decl, p);
    decl.result = decl.result.accept(this, p);
    decl.fields.forEach(f -> f.accept(this, p));
    return Unit.unit();
  }

  @Override default Unit visitFn(@NotNull Decl.FnDecl decl, P p) {
    visitDecl(decl, p);
    decl.result = decl.result.accept(this, p);
    decl.body = decl.body.map(
      expr -> expr.accept(this, p),
      clauses -> clauses.map(clause -> visitClause(clause, p))
    );
    return Unit.unit();
  }

  @Override default Unit visitPrim(@NotNull Decl.PrimDecl decl, P p) {
    visitDecl(decl, p);
    if (decl.result != null) decl.result = decl.result.accept(this, p);
    return Unit.unit();
  }

  @Override default Unit visitImport(Stmt.@NotNull ImportStmt cmd, P p) {
    return Unit.unit();
  }

  @Override default Unit visitOpen(Stmt.@NotNull OpenStmt cmd, P p) {
    return Unit.unit();
  }

  @Override default Unit visitModule(Stmt.@NotNull ModuleStmt mod, P p) {
    mod.contents().forEach(stmt -> stmt.accept(this, p));
    return Unit.unit();
  }
  @Override default Unit visitCtor(Decl.@NotNull DataCtor ctor, P p) {
    visitSignatured(ctor, p);
    ctor.clauses = ctor.clauses.map(clause -> visitClause(clause, p));
    return Unit.unit();
  }
  @Override default Unit visitField(Decl.@NotNull StructField field, P p) {
    visitSignatured(field, p);
    field.result = field.result.accept(this, p);
    field.clauses = field.clauses.map(clause -> visitClause(clause, p));
    field.body = field.body.map(expr -> expr.accept(this, p));
    return Unit.unit();
  }

  @Override default Unit visitBind(Stmt.@NotNull BindStmt bind, P p) {
    return Unit.unit();
  }

  @Override default Unit visitLevels(Generalize.@NotNull Levels levels, P p) {
    return Unit.unit();
  }
}
