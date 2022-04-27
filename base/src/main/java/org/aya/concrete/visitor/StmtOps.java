// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.visitor;

import org.aya.concrete.Pattern;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.stmt.*;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

/**
 * @author ice1000
 * TODO: rewrite this class using pattern matching
 */
public interface StmtOps<P> extends ExprTraversal<P> {
  default <T extends Decl> void traced(@NotNull T yeah, P p, @NotNull BiConsumer<T, P> f) {
    traceEntrance(yeah, p);
    f.accept(yeah, p);
    traceExit(p);
  }

  default void traceEntrance(@NotNull Decl item, P p) {
  }
  default void traceExit(P p) {
  }

  default void visitTelescopic(@NotNull BaseDecl.Telescopic signatured, P pp) {
    signatured.telescope = signatured.telescope.map(p -> p.mapExpr(expr -> visitExpr(expr, pp)));
  }

  default void visit(@NotNull Stmt stmt, P pp) {
    switch (stmt) {
      case Remark remark -> {
        if (remark.literate != null) remark.literate.modify(expr -> visitExpr(expr, pp));
      }
      case TopTeleDecl decl -> visitDecl(decl, pp);
      case Command cmd -> visitCommand(cmd, pp);
      case Generalize generalize -> generalize.type = visitExpr(generalize.type, pp);
      case ClassDecl cls -> {}
    }
  }
  default void visitCommand(@NotNull Command cmd, P pp) {
    switch (cmd) {
      case Command.Module moduleCmd -> moduleCmd.contents().forEach(stmt -> visit(stmt, pp));
      case Command.Import importCmd -> {}
      case Command.Open open -> {}
    }
  }

  default void visitDecl(@NotNull TopLevelDecl decl, P pp) {
    if(decl instanceof TopTeleDecl declWithSig) visitTelescopic(declWithSig, pp);
    decl.setResult(visitExpr(decl.result(), pp));
    switch (decl) {
      case TopTeleDecl.DataDecl data -> data.body.forEach(ctor -> traced(ctor, pp, this::visitCtor));
      case StructDecl struct -> struct.fields.forEach(field -> traced(field, pp, this::visitField));
      case TopTeleDecl.FnDecl fn -> fn.body = fn.body.map(
        expr -> visitExpr(expr, pp),
        clauses -> clauses.map(clause -> visitClause(clause, pp))
      );
      case TopTeleDecl.PrimDecl prim -> {}
    }
  }

  default @NotNull Pattern.Clause visitClause(@NotNull Pattern.Clause c, P pp) {
    return new Pattern.Clause(c.sourcePos, c.patterns.map(p -> visitPattern(p, pp)), c.expr.map(expr -> visitExpr(expr, pp)));
  }

  default @NotNull Pattern visitPattern(@NotNull Pattern pattern, P pp) {
    return switch (pattern) {
      case Pattern.BinOpSeq seq -> visitBinOpPattern(seq, pp);
      case Pattern.Ctor ctor ->
        new Pattern.Ctor(ctor.sourcePos(), ctor.explicit(), ctor.resolved(), ctor.params().map(p -> visitPattern(p, pp)), ctor.as());
      case Pattern.Tuple tup ->
        new Pattern.Tuple(tup.sourcePos(), tup.explicit(), tup.patterns().map(p -> visitPattern(p, pp)), tup.as());
      default -> pattern;
    };
  }

  default @NotNull Pattern visitBinOpPattern(@NotNull Pattern.BinOpSeq seq, P pp) {
    return new Pattern.BinOpSeq(seq.sourcePos(), seq.seq().map(p -> visitPattern(p, pp)), seq.as(), seq.explicit());
  }

  default void visitCtor(TopTeleDecl.@NotNull DataCtor ctor, P p) {
    visitTelescopic(ctor, p);
    ctor.patterns = ctor.patterns.map(pat -> visitPattern(pat, p));
    ctor.clauses = ctor.clauses.map(clause -> visitClause(clause, p));
  }
  default void visitField(TopTeleDecl.@NotNull StructField field, P p) {
    visitTelescopic(field, p);
    field.result = visitExpr(field.result, p);
    field.clauses = field.clauses.map(clause -> visitClause(clause, p));
    field.body = field.body.map(expr -> visitExpr(expr, p));
  }
}
