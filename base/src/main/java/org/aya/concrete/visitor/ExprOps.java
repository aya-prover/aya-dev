// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.visitor;

import org.aya.concrete.Expr;
import org.aya.generic.util.InternalException;
import org.aya.pretty.doc.Doc;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public interface ExprOps extends ExprView {
  @NotNull ExprView view();
  @Override default @NotNull Expr initial() {
    return view().initial();
  }

  @Override default @NotNull Expr pre(@NotNull Expr expr) {
    return view().pre(expr);
  }

  @Override default @NotNull Expr post(@NotNull Expr expr) {
    return view().post(expr);
  }

  class HoleFiller implements ExprOps {
    final Expr placeHolder = new Expr.ErrorExpr(SourcePos.NONE, Doc.english("Internal Error for expr hole filler"));
    boolean filled = false;
    Expr filling = placeHolder;
    Expr exprWithHole = placeHolder;

    @Override public @NotNull ExprView view() {
      return exprWithHole.view();
    }

    @Override public @NotNull Expr pre(@NotNull Expr expr) {
      return switch (expr) {
        case Expr.HoleExpr hole -> {
          if (!filled) {
            filled = true;
            yield filling;
          } else yield hole;
        }
        case Expr misc -> misc;
      };
    }

    public @NotNull Expr fill(Expr exprWithHole, Expr filling) {
      filled = false;
      this.exprWithHole = exprWithHole;
      this.filling = filling;
      var filledExpr = commit();
      if (!filled) throw new InternalException("Tactic elaborator cannot fill the hole of " + exprWithHole);

      return filledExpr;
    }
  }
}
