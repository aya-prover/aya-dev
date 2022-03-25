// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.visitor;

import kala.tuple.Unit;
import org.aya.concrete.Pattern;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.stmt.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
public interface StmtConsumer<P> extends Stmt.Visitor<P, Unit>, ExprConsumer<P> {
  default void visitSignatured(@NotNull Signatured signatured, P pp) {
    signatured.telescope.forEach(p -> {
      var type = p.type();
      type.accept(this, pp);
    });
  }

  default void visitDecl(@NotNull Decl decl, P pp) {
    visitSignatured(decl, pp);
    decl.result.accept(this, pp);
  }

  default void visitClause(@NotNull Pattern.Clause c, P pp) {
    c.patterns.forEach(pattern -> visitPattern(pattern, pp));
    c.expr.forEach(expr -> expr.accept(this, pp));
  }

  default void visitPattern(@NotNull Pattern pattern, P p) {
    switch (pattern) {
      case Pattern.Ctor ctor -> ctor.params().forEach(pat -> visitPattern(pat, p));
      case Pattern.Tuple tuple -> tuple.patterns().forEach(pat -> visitPattern(pat, p));
      case Pattern.BinOpSeq seq -> seq.seq().forEach(pat -> visitPattern(pat, p));
      default -> {
      }
    }
  }

  @Override default Unit visitData(@NotNull Decl.DataDecl decl, P p) {
    visitDecl(decl, p);
    decl.body.forEach(ctor -> traced(ctor, p, this::visitCtor));
    return Unit.unit();
  }

  @Override default Unit visitStruct(@NotNull Decl.StructDecl decl, P p) {
    visitDecl(decl, p);
    decl.fields.forEach(field -> traced(field, p, this::visitField));
    return Unit.unit();
  }

  @Override default Unit visitFn(@NotNull Decl.FnDecl decl, P p) {
    visitDecl(decl, p);
    decl.body.forEach(
      expr -> expr.accept(this, p),
      clauses -> clauses.forEach(clause -> visitClause(clause, p))
    );
    return Unit.unit();
  }

  @Override default Unit visitPrim(@NotNull Decl.PrimDecl decl, P p) {
    visitDecl(decl, p);
    return Unit.unit();
  }

  @Override default Unit visitImport(Command.@NotNull Import cmd, P p) {
    return Unit.unit();
  }

  @Override default Unit visitOpen(Command.@NotNull Open cmd, P p) {
    return Unit.unit();
  }

  @Override default Unit visitModule(Command.@NotNull Module mod, P p) {
    mod.contents().forEach(stmt -> stmt.accept(this, p));
    return Unit.unit();
  }

  @Override default Unit visitCtor(Decl.@NotNull DataCtor ctor, P p) {
    visitSignatured(ctor, p);
    ctor.patterns.forEach(pattern -> visitPattern(pattern, p));
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

  @Override default Unit visitRemark(@NotNull Remark remark, P p) {
    if (remark.literate != null) remark.literate.visit(this, p);
    return Unit.unit();
  }

  @Override default Unit visitVariables(Generalize.@NotNull Variables variables, P p) {
    variables.type.accept(this, p);
    return Unit.unit();
  }
}
