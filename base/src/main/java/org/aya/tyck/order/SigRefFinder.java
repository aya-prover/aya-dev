// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.order;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.tuple.Unit;
import org.aya.concrete.Expr;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.stmt.Command;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Generalize;
import org.aya.concrete.visitor.ExprConsumer;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

/**
 * Concrete version of RefFinder but only header is searched.
 *
 * @author kiva
 * @see org.aya.core.visitor.RefFinder
 */
public class SigRefFinder implements ExprConsumer<@NotNull MutableList<TyckUnit>> {
  public static final @NotNull SigRefFinder HEADER_ONLY = new SigRefFinder();

  private void decl(@NotNull MutableList<TyckUnit> references, @NotNull Decl decl) {
    tele(decl.telescope, references);
    decl.result.accept(this, references);
  }

  public void visit(@NotNull TyckUnit sn, @NotNull MutableList<TyckUnit> references) {
    switch (sn) {
      case Decl decl -> decl(references, decl);
      case Decl.DataCtor ctor -> tele(ctor.telescope, references);
      case Decl.StructField field -> {
        tele(field.telescope, references);
        field.result.accept(this, references);
      }
      case Command.Module module -> {}
      case Command cmd -> {}
      case Remark remark -> {
        assert remark.literate != null;
        remark.literate.visit(this, references);
      }
      case Generalize variables -> variables.type.accept(this, references);
    }
  }

  private void tele(@NotNull ImmutableSeq<Expr.Param> tele, @NotNull MutableList<TyckUnit> references) {
    tele.mapNotNull(Expr.Param::type).forEach(type -> type.accept(this, references));
  }

  @Override public Unit visitRef(@NotNull Expr.RefExpr expr, @NotNull MutableList<TyckUnit> references) {
    if (expr.resolvedVar() instanceof DefVar<?, ?> defVar && defVar.concrete instanceof Decl decl)
      references.append(decl);
    return Unit.unit();
  }
}
