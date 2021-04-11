// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.parse;

import org.aya.api.error.SourcePos;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.Arg;
import org.aya.api.util.Assoc;
import org.aya.concrete.Decl;
import org.aya.concrete.Expr;
import org.aya.util.Constants;
import org.glavo.kala.collection.SeqView;
import org.jetbrains.annotations.NotNull;

public record BinOpParser(@NotNull SeqView<@NotNull Elem> seq) {
  @NotNull public Expr build(@NotNull SourcePos sourcePos) {
    var first = seq.first();
    if (first.assoc().infix) {
      // + f a b c d
      // \lam _ => _ + f a b c d
      var lhs = new LocalVar(Constants.ANONYMOUS_PREFIX);
      var lhsElem = new Elem(new Expr.RefExpr(SourcePos.NONE, lhs, "_"), true);
      // TODO[kiva]: workaround for https://github.com/Glavo/kala-common/issues/33
      var lamSeq = seq.toImmutableSeq().prepended(lhsElem).view();
      return new Expr.LamExpr(sourcePos,
        new Expr.Param(SourcePos.NONE, lhs, true),
        new BinOpParser(lamSeq).build(sourcePos));
    }
    // TODO[kiva]: Unfinished, but i think the following code is
    //  just supposed to convert infix to prefix??? is it??
    //  but we need priority system first.
    return new Expr.AppExpr(
      sourcePos,
      seq.first().expr(),
      seq.view().drop(1)
        .map(e -> new Arg<>(e.expr(), e.explicit()))
        .toImmutableSeq()
    );
  }

  /**
   * something like {@link org.aya.api.util.Arg<Expr>}
   * but only used in binary operator building
   */
  public record Elem(@NotNull Expr expr, boolean explicit) {
    public @NotNull Assoc assoc() {
      if (expr instanceof Expr.RefExpr ref
        && ref.resolvedVar() instanceof DefVar<?, ?> defVar
        && defVar.concrete instanceof Decl.Operator opDecl) {
        var op = opDecl.asOperator();
        return op != null && ref.resolvedFrom().equals(op._1) ? op._2 : Assoc.NoFix;
      }
      return Assoc.NoFix;
    }
  }
}
