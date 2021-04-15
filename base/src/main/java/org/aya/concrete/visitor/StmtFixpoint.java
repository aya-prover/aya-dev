// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.visitor;

import org.aya.concrete.*;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public interface StmtFixpoint extends ExprFixpoint<Unit>, Stmt.Visitor<Unit, Unit>, Signatured.Visitor<Unit, Unit>, Decl.Visitor<Unit, Unit> {
  default void visitSignatured(@NotNull Signatured signatured) {
    signatured.telescope = signatured.telescope.map(p -> p.mapExpr(expr -> expr.accept(this, Unit.unit())));
  }

  default void visitDecl(@NotNull Decl decl) {
    visitSignatured(decl);
    decl.abuseBlock.forEach(stmt -> stmt.accept(this, Unit.unit()));
  }

  default @NotNull Pattern.Clause visitClause(@NotNull Pattern.Clause c) {
    return new Pattern.Clause(c.sourcePos(), c.patterns(), c.expr().map(expr -> expr.accept(this, Unit.unit())));
  }

  @Override default Unit visitData(@NotNull Decl.DataDecl decl, Unit unit) {
    visitDecl(decl);
    decl.result = decl.result.accept(this, Unit.unit());
    decl.body.forEach(ctor -> ctor.accept(this, Unit.unit()));
    return unit;
  }

  @Override default Unit visitStruct(@NotNull Decl.StructDecl decl, Unit unit) {
    visitDecl(decl);
    decl.result = decl.result.accept(this, Unit.unit());
    decl.fields.forEach(f -> f.accept(this, Unit.unit()));
    return unit;
  }

  @Override default Unit visitFn(@NotNull Decl.FnDecl decl, Unit unit) {
    visitDecl(decl);
    decl.result = decl.result.accept(this, Unit.unit());
    decl.body = decl.body.map(
      expr -> expr.accept(this, Unit.unit()),
      clauses -> clauses.map(this::visitClause)
    );
    return unit;
  }

  @Override default Unit visitPrim(@NotNull Decl.PrimDecl decl, Unit unit) {
    visitDecl(decl);
    if (decl.result != null) decl.result = decl.result.accept(this, Unit.unit());
    return unit;
  }

  @Override default void traceEntrance(@NotNull Decl decl, Unit unit) {
  }
  @Override default Unit visitImport(Stmt.@NotNull ImportStmt cmd, Unit unit) {
    return unit;
  }

  @Override default Unit visitOpen(Stmt.@NotNull OpenStmt cmd, Unit unit) {
    return unit;
  }

  @Override default Unit visitModule(Stmt.@NotNull ModuleStmt mod, Unit unit) {
    mod.contents().forEach(stmt -> stmt.accept(this, Unit.unit()));
    return unit;
  }
  @Override default Unit visitCtor(Decl.@NotNull DataCtor ctor, Unit unit) {
    visitSignatured(ctor);
    ctor.clauses = ctor.clauses.map(this::visitClause);
    return unit;
  }
  @Override default Unit visitField(Decl.@NotNull StructField field, Unit unit) {
    visitSignatured(field);
    field.result = field.result.accept(this, Unit.unit());
    field.clauses = field.clauses.map(this::visitClause);
    field.body = field.body.map(expr -> expr.accept(this, Unit.unit()));
    return unit;
  }

  @Override default Unit visitBind(Stmt.@NotNull BindStmt bind, Unit unit) {
    return unit;
  }

  @Override default Unit visitLevels(Generalize.@NotNull Levels levels, Unit unit) {
    return unit;
  }
}
