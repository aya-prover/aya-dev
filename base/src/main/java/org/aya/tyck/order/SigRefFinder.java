// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.order;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.concrete.Expr;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.stmt.Command;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Generalize;
import org.aya.concrete.visitor.ExprTraversal;
import org.aya.core.visitor.MonoidalTermFolder;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

/**
 * Concrete version of RefFinder but only header is searched.
 *
 * @author kiva
 * @see MonoidalTermFolder.RefFinder
 */
public class SigRefFinder implements ExprTraversal<@NotNull MutableList<TyckUnit>> {
  public static final @NotNull SigRefFinder HEADER_ONLY = new SigRefFinder();

  public void visit(@NotNull TyckUnit sn, @NotNull MutableList<TyckUnit> references) {
    switch (sn) {
      case Decl decl -> {
        if (decl instanceof Decl.Telescopic proof) tele(proof.telescope(), references);
        if (decl instanceof Decl.Resulted proof) visitExpr(proof.result(), references);
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

  @Override public @NotNull Expr visitExpr(@NotNull Expr expr, @NotNull MutableList<TyckUnit> references) {
    if (expr instanceof Expr.RefExpr ref && ref.resolvedVar() instanceof DefVar<?, ?> defVar) {
      // in the past when we had Signatured, the Decl class only derives top-level definitions
      if (defVar.concrete instanceof Decl.TopLevel)
        references.append(defVar.concrete);
    }
    return ExprTraversal.super.visitExpr(expr, references);
  }
}
