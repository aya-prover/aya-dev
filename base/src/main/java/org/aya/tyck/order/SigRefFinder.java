// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.order;

import kala.collection.mutable.MutableList;
import org.aya.concrete.Expr;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.stmt.Command;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Generalize;
import org.aya.concrete.visitor.ExprConsumer;
import org.aya.core.visitor.TermFolder;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

/**
 * Concrete version of RefFinder but only header is searched.
 *
 * @author kiva
 * @see TermFolder.RefFinder
 */
// TODO(wsx): Folder?
public record SigRefFinder(@NotNull MutableList<TyckUnit> references) implements ExprConsumer {
  public void accept(@NotNull TyckUnit sn) {
    switch (sn) {
      case Decl decl -> {
        if (decl instanceof Decl.Telescopic<?> proof)
          proof.telescope().mapNotNull(Expr.Param::type).forEach(this);
        if (decl instanceof Decl.Resulted proof) accept(proof.result());
      }
      case Command.Module module -> {}
      case Command cmd -> {}
      case Remark remark -> {
        assert remark.literate != null;
        // TODO[CHECK]: recurse into remark
        // remark.literate.visit(this);
      }
      case Generalize variables -> accept(variables.type);
    }
  }

  @Override public void pre(@NotNull Expr expr) {
    if (expr instanceof Expr.Ref ref && ref.resolvedVar() instanceof DefVar<?, ?> def
        && def.concrete instanceof Decl.TopLevel) {
      references.append(def.concrete);
    } else {
      ExprConsumer.super.pre(expr);
    }
  }
}
