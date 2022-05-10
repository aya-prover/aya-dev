// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.order;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.concrete.Expr;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.stmt.ClassDecl;
import org.aya.concrete.stmt.Command;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Generalize;
import org.aya.concrete.visitor.ExprTraversal;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

/**
 * Concrete version of RefFinder but only header is searched.
 *
 * @author kiva
 * @see org.aya.core.visitor.RefFinder
 */
public class SigRefFinder implements ExprTraversal<@NotNull MutableList<TyckUnit>> {
  public static final @NotNull SigRefFinder HEADER_ONLY = new SigRefFinder();

  private void decl(@NotNull MutableList<TyckUnit> references, @NotNull Decl decl) {
    tele(decl.telescope, references);
    visitExpr(decl.result, references);
  }

  public void visit(@NotNull TyckUnit sn, @NotNull MutableList<TyckUnit> references) {
    switch (sn) {
      case Decl decl -> decl(references, decl);
      case ClassDecl decl -> throw new UnsupportedOperationException("TODO");
      case Decl.DataCtor ctor -> tele(ctor.telescope, references);
      case Decl.StructField field -> {
        tele(field.telescope, references);
        visitExpr(field.result, references);
      }
      case Command.Module module -> {}
      case Command cmd -> {}
      case Remark remark -> {
        assert remark.literate != null;
        remark.literate.visit(this, references);
      }
      case Generalize variables -> visitExpr(variables.type, references);
    }
  }

  private void tele(@NotNull ImmutableSeq<Expr.Param> tele, @NotNull MutableList<TyckUnit> references) {
    tele.mapNotNull(Expr.Param::type).forEach(type -> visitExpr(type, references));
  }

  @Override public @NotNull Expr visitExpr(@NotNull Expr expr, @NotNull MutableList<TyckUnit> tyckUnits) {
    if (expr instanceof Expr.RefExpr ref) {
      if (ref.resolvedVar() instanceof DefVar<?, ?> defVar && defVar.concrete instanceof Decl decl)
        tyckUnits.append(decl);
    }
    return ExprTraversal.super.visitExpr(expr, tyckUnits);
  }
}
