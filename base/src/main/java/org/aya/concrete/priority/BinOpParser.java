// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.priority;

import org.aya.api.error.SourcePos;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.Arg;
import org.aya.concrete.Decl;
import org.aya.concrete.Expr;
import org.aya.util.Constants;
import org.glavo.kala.collection.SeqView;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.DoubleLinkedBuffer;
import org.glavo.kala.collection.mutable.LinkedBuffer;
import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.tuple.Tuple2;
import org.glavo.kala.tuple.Tuple3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BinOpParser {
  private final @NotNull BinOpSet opSet;
  private final @NotNull SeqView<@NotNull Elem> seq;

  public BinOpParser(@NotNull BinOpSet opSet, @NotNull SeqView<@NotNull Elem> seq) {
    this.opSet = opSet;
    this.seq = seq;
  }

  private final LinkedBuffer<Tuple2<BinOpParser.Elem, BinOpSet.Elem>> opStack = LinkedBuffer.of();
  private final DoubleLinkedBuffer<BinOpParser.Elem> prefixes = DoubleLinkedBuffer.of();

  @NotNull public Expr build(@NotNull SourcePos sourcePos) {
    var first = seq.first();
    if (opSet.assocOf(first.asOpDecl()).infix) {
      // + f a b c d
      // \lam _ => _ + f a b c d
      var lhs = new LocalVar(Constants.ANONYMOUS_PREFIX);
      var lhsElem = new Elem(new Expr.RefExpr(SourcePos.NONE, lhs, "_"), true);
      // TODO[kiva]: workaround for https://github.com/Glavo/kala-common/issues/33
      var lamSeq = seq.toImmutableSeq().prepended(lhsElem).view();
      return new Expr.LamExpr(sourcePos,
        new Expr.Param(SourcePos.NONE, lhs, true),
        new BinOpParser(opSet, lamSeq).build(sourcePos));
    }

    // TODO[kiva]: the following code is just supposed to convert
    //  infix expr to prefix expr??? is it??

    for (var expr : seq.reversed()) {
      var tryOp = expr.asOpDecl();
      if (opSet.isNotUsedAsOperator(tryOp)) prefixes.push(expr);
      else {
        var currentOp = opSet.ensureHasElem(tryOp._1, tryOp._2);
        while (opStack.isNotEmpty()) {
          var cmp = opStack.peek()._2.compareWith(currentOp);
          // TODO[kiva]: report
          if (cmp == BinOpSet.PredCmp.Undefined)
            throw new IllegalArgumentException("ambiguous operator precedence between " + currentOp.name()
              + " and " + opStack.peek()._2.name());
          if (cmp == BinOpSet.PredCmp.Tighter) {
            var topOp = opStack.pop();
            var appExpr = makeBinApp(topOp._1);
            prefixes.push(new Elem(appExpr, topOp._1.explicit));
          } else break;
        }
        opStack.push(Tuple.of(expr, currentOp));
      }
    }

    while (opStack.isNotEmpty()) {
      var op = opStack.pop();
      var app = makeBinApp(op._1);
      prefixes.push(new Elem(app, op._1.explicit));
    }

    assert prefixes.isNotEmpty();
    if (prefixes.sizeEquals(1)) return prefixes.first().expr;

    var r = new Expr.AppExpr(
      sourcePos,
      prefixes.first().expr,
      prefixes.view().drop(1)
        .map(e -> new Arg<>(e.expr(), e.explicit()))
        .toImmutableSeq()
    );
    return r;
  }

  private Expr.@NotNull AppExpr makeBinApp(@NotNull Elem op) {
    var rhs = prefixes.pop();
    var lhs = prefixes.pop();
    return new Expr.AppExpr(
      computeSourcePos(op.expr.sourcePos(), lhs.expr.sourcePos(), rhs.expr.sourcePos()),
      op.expr,
      ImmutableSeq.of(lhs.toArg(), rhs.toArg())
    );
  }

  private @NotNull SourcePos computeSourcePos(@NotNull SourcePos a, @NotNull SourcePos b, @NotNull SourcePos c) {
    return a.union(b).union(c);
  }

  /**
   * something like {@link Arg<Expr>}
   * but only used in binary operator building
   */
  public record Elem(@NotNull Expr expr, boolean explicit) {
    public @Nullable Tuple3<String, Decl.@NotNull OpDecl, String> asOpDecl() {
      if (expr instanceof Expr.RefExpr ref
        && ref.resolvedVar() instanceof DefVar<?, ?> defVar
        && defVar.concrete instanceof Decl.OpDecl opDecl) {
        return Tuple.of(defVar.name(), opDecl, ref.resolvedFrom());
      }
      return null;
    }

    public @NotNull Arg<Expr> toArg() {
      return new Arg<>(expr, explicit);
    }
  }
}
