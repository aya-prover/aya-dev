// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.order;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.tuple.Unit;
import org.aya.api.ref.DefVar;
import org.aya.concrete.Expr;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.stmt.*;
import org.aya.concrete.visitor.ExprConsumer;
import org.jetbrains.annotations.NotNull;

/**
 * Concrete version of RefFinder but only header is searched.
 *
 * @author kiva
 * @see org.aya.core.visitor.RefFinder
 */
public class SigRefFinder implements ExprConsumer<@NotNull DynamicSeq<Stmt>> {
  public static final @NotNull SigRefFinder HEADER_ONLY = new SigRefFinder();

  private void decl(@NotNull DynamicSeq<Stmt> stmts, @NotNull Decl decl) {
    tele(stmts, decl.telescope);
    decl.result.accept(this, stmts);
  }

  public void visit(@NotNull Signatured stmt, @NotNull DynamicSeq<Stmt> stmts) {
    switch (stmt) {
      case Decl decl -> decl(stmts, decl);
      case Decl.DataCtor ctor -> {}
      case Decl.StructField field -> {}
    }
  }

  public void visit(@NotNull Stmt stmt, @NotNull DynamicSeq<Stmt> stmts) {
    switch (stmt) {
      case Decl decl -> decl(stmts, decl);
      case Command.Module module -> {}
      case Command cmd -> {}
      case Remark remark -> {
        assert remark.literate != null;
        remark.literate.visit(this, stmts);
      }
      case Sample sample -> visit(sample.delegate(), stmts);
      case Generalize.Variables variables -> variables.type.accept(this, stmts);
      case Generalize.Levels levels -> {}
    }
  }

  private void tele(@NotNull DynamicSeq<Stmt> stmts, @NotNull ImmutableSeq<Expr.Param> telescope) {
    telescope.mapNotNull(Expr.Param::type).forEach(type -> type.accept(this, stmts));
  }

  @Override public Unit visitRef(@NotNull Expr.RefExpr expr, @NotNull DynamicSeq<Stmt> stmts) {
    if (expr.resolvedVar() instanceof DefVar<?, ?> defVar && defVar.concrete instanceof Decl decl)
      stmts.append(decl);
    return Unit.unit();
  }
}
