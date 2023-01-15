// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.order;

import kala.collection.mutable.MutableList;
import org.aya.concrete.Expr;
import org.aya.concrete.stmt.Command;
import org.aya.concrete.stmt.Generalize;
import org.aya.concrete.stmt.decl.Decl;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.concrete.visitor.ExprConsumer;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

/**
 * Concrete version of RefFinder but only header is searched.
 *
 * @author kiva
 */
// TODO(wsx): Folder? -- (kiva: please!!)
public record SigRefFinder(@NotNull MutableList<TyckUnit> references) implements ExprConsumer {
  public void accept(@NotNull TyckUnit sn) {
    switch (sn) {
      case Decl decl -> {
        if (decl instanceof TeleDecl<?> proof) telescopic(proof);
        // for ctor: partial is a part of header
        if (decl instanceof TeleDecl.DataCtor ctor) accept(ctor.clauses);
      }
      case Command.Module module -> module.contents().forEach(this::accept);
      case Command cmd -> {}
      case Generalize variables -> accept(variables.type);
    }
  }

  public void telescopic(@NotNull TeleDecl<?> proof) {
    proof.telescope.mapNotNull(Expr.Param::type).forEach(this);
    if (proof.result != null) accept(proof.result);
  }

  @Override public void pre(@NotNull Expr expr) {
    if (expr instanceof Expr.Ref ref && ref.resolvedVar() instanceof DefVar<?, ?> def && def.concrete != null) {
      references.append(def.concrete);
    } else {
      ExprConsumer.super.pre(expr);
    }
  }
}
