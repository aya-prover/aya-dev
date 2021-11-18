// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.visitor;

import kala.tuple.Unit;
import org.aya.concrete.Pattern;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.stmt.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public interface StmtFixpoint<P> extends ExprFixpoint<P>, Stmt.Visitor<P, Unit> {
  default void visitSignatured(@NotNull Signatured signatured, P pp) {
    signatured.telescope = signatured.telescope.map(p -> p.mapExpr(expr -> expr.accept(this, pp)));
  }

  @Override default Unit visitRemark(@NotNull Remark remark, P p) {
    if (remark.literate != null) remark.literate.modify(this, p);
    return Unit.unit();
  }

  default void visitDecl(@NotNull Decl decl, P pp) {
    visitSignatured(decl, pp);
  }

  default @NotNull Pattern.Clause visitClause(@NotNull Pattern.Clause c, P pp) {
    return new Pattern.Clause(c.sourcePos, c.patterns.map(p -> visitPattern(p, pp)), c.expr.map(expr -> expr.accept(this, pp)));
  }

  default @NotNull Pattern visitPattern(@NotNull Pattern pattern, P pp) {
    return switch (pattern) {
      case Pattern.BinOpSeq seq -> visitBinOpPattern(seq, pp);
      case Pattern.Ctor ctor -> new Pattern.Ctor(ctor.sourcePos(), ctor.explicit(), ctor.resolved(), ctor.params().map(p -> visitPattern(p, pp)), ctor.as());
      case Pattern.Tuple tup -> new Pattern.Tuple(tup.sourcePos(), tup.explicit(), tup.patterns().map(p -> visitPattern(p, pp)), tup.as());
      default -> pattern;
    };
  }

  default @NotNull Pattern visitBinOpPattern(@NotNull Pattern.BinOpSeq seq, P pp) {
    return new Pattern.BinOpSeq(seq.sourcePos(), seq.seq().map(p -> visitPattern(p, pp)), seq.as(), seq.explicit());
  }

  @Override default Unit visitData(@NotNull Decl.DataDecl decl, P p) {
    visitDecl(decl, p);
    decl.result = decl.result.accept(this, p);
    decl.body.forEach(ctor -> traced(ctor, p, this::visitCtor));
    return Unit.unit();
  }

  @Override default Unit visitStruct(@NotNull Decl.StructDecl decl, P p) {
    visitDecl(decl, p);
    decl.result = decl.result.accept(this, p);
    decl.fields.forEach(field -> traced(field, p, this::visitField));
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
    ctor.patterns = ctor.patterns.map(pat -> visitPattern(pat, p));
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

  @Override default Unit visitLevels(Generalize.@NotNull Levels levels, P p) {
    return Unit.unit();
  }

  @Override default Unit visitVariables(Generalize.@NotNull Variables variables, P p) {
    variables.type = variables.type.accept(this, p);
    return Unit.unit();
  }

  @Override default Unit visitExample(Sample.@NotNull Working example, P p) {
    return example.delegate().accept(this, p);
  }

  @Override default Unit visitCounterexample(Sample.@NotNull Counter example, P p) {
    return example.delegate().accept(this, p);
  }
}
