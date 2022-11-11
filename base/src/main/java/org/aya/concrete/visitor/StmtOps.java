// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.visitor;

import org.aya.concrete.Pattern;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.stmt.*;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

/**
 * @author ice1000
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

  default void visitTelescopic(@NotNull Decl decl, @NotNull Decl.Telescopic proof, P pp) {
    assert decl == proof;
    proof.setTelescope(proof.telescope().map(p -> p.descent(expr -> visitExpr(expr, pp))));
  }

  default void visit(@NotNull Stmt stmt, P pp) {
    switch (stmt) {
      case Remark remark -> {
        if (remark.literate != null) remark.literate.modify(expr -> visitExpr(expr, pp));
      }
      case Decl decl -> visitDecl(decl, pp);
      case Command cmd -> visitCommand(cmd, pp);
      case Generalize generalize -> generalize.type = visitExpr(generalize.type, pp);
    }
  }

  default void visitCommand(@NotNull Command cmd, P pp) {
    switch (cmd) {
      case Command.Module moduleCmd -> moduleCmd.contents().forEach(stmt -> visit(stmt, pp));
      case Command.Import importCmd -> {}
      case Command.Open open -> {}
    }
  }

  default void visitDecl(@NotNull Decl decl, P pp) {
    if (decl instanceof Decl.Telescopic teleDecl) visitTelescopic(decl, teleDecl, pp);
    if (decl instanceof Decl.Resulted resulted) resulted.setResult(visitExpr(resulted.result(), pp));
    switch (decl) {
      case ClassDecl classDecl -> {}
      case TeleDecl.PrimDecl prim -> {}
      case TeleDecl.DataDecl data -> data.body.forEach(ctor -> traced(ctor, pp, this::visitDecl));
      case TeleDecl.StructDecl struct -> struct.fields.forEach(field -> traced(field, pp, this::visitDecl));
      case TeleDecl.FnDecl fn -> fn.body = fn.body.map(
        expr -> visitExpr(expr, pp),
        clauses -> clauses.map(clause -> visitClause(clause, pp))
      );
      case TeleDecl.DataCtor ctor -> {
        ctor.patterns = ctor.patterns.map(pat -> pat.descent(pattern -> visitPattern(pattern, pp)));
        ctor.clauses = ctor.clauses.map(clause -> visitClause(clause, pp));
      }
      case TeleDecl.StructField field -> field.body = field.body.map(expr -> visitExpr(expr, pp));
    }
  }

  default @NotNull Pattern.Clause visitClause(@NotNull Pattern.Clause c, P pp) {
    return new Pattern.Clause(c.sourcePos,
      c.patterns.map(p -> p.descent(pat -> visitPattern(pat, pp))),
      c.expr.map(expr -> visitExpr(expr, pp)));
  }
}
