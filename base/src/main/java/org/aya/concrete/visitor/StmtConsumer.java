// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.visitor;

import org.aya.concrete.*;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
public interface StmtConsumer<P> extends Stmt.Visitor<P, Unit>, ExprConsumer<P>, Signatured.Visitor<P, Unit>, Decl.Visitor<P, Unit> {
  default void visitSignatured(@NotNull Signatured signatured, P pp) {
    signatured.telescope.forEach(p -> {
      var type = p.type();
      if (type != null) type.accept(this, pp);
    });
  }

  default void visitDecl(@NotNull Decl decl, P pp) {
    visitSignatured(decl, pp);
    decl.abuseBlock.forEach(stmt -> stmt.accept(this, pp));
  }

  default void visitClause(@NotNull Pattern.Clause c, P pp) {
    c.expr.forEach(expr -> expr.accept(this, pp));
  }

  @Override default Unit visitData(@NotNull Decl.DataDecl decl, P p) {
    visitDecl(decl, p);
    decl.result.accept(this, p);
    decl.body.forEach(ctor -> ctor.accept(this, p));
    return Unit.unit();
  }

  @Override default Unit visitStruct(@NotNull Decl.StructDecl decl, P p) {
    visitDecl(decl, p);
    decl.result.accept(this, p);
    decl.fields.forEach(f -> f.accept(this, p));
    return Unit.unit();
  }

  @Override default Unit visitFn(@NotNull Decl.FnDecl decl, P p) {
    visitDecl(decl, p);
    decl.result.accept(this, p);
    decl.body.forEach(
      expr -> expr.accept(this, p),
      clauses -> clauses.forEach(clause -> visitClause(clause, p))
    );
    return Unit.unit();
  }

  @Override default Unit visitPrim(@NotNull Decl.PrimDecl decl, P p) {
    visitDecl(decl, p);
    if (decl.result != null) decl.result.accept(this, p);
    return Unit.unit();
  }

  @Override default void traceEntrance(@NotNull Decl decl, P p) {
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
    ctor.clauses.forEach(clause -> visitClause(clause, p));
    return Unit.unit();
  }

  @Override default Unit visitField(Decl.@NotNull StructField field, P p) {
    visitSignatured(field, p);
    field.result.accept(this, p);
    field.clauses.forEach(clause -> visitClause(clause, p));
    field.body.forEach(expr -> expr.accept(this, p));
    return Unit.unit();
  }

  @Override default Unit visitBind(Stmt.@NotNull BindStmt bind, P p) {
    return Unit.unit();
  }

  @Override default Unit visitLevels(Generalize.@NotNull Levels levels, P p) {
    return Unit.unit();
  }
}
