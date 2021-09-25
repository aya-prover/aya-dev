// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.desugar;

import kala.collection.SeqView;
import kala.collection.mutable.Buffer;
import kala.collection.mutable.DoubleLinkedBuffer;
import kala.collection.mutable.LinkedBuffer;
import kala.control.Option;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.tuple.Tuple3;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.Arg;
import org.aya.concrete.Expr;
import org.aya.concrete.desugar.error.OperatorProblem;
import org.aya.concrete.stmt.OpDecl;
import org.aya.pretty.doc.Doc;
import org.aya.util.Constants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BinOpParser {
  private final @NotNull BinOpSet opSet;
  private final @NotNull SeqView<@NotNull Elem> seq;

  public BinOpParser(@NotNull BinOpSet opSet, @NotNull SeqView<@NotNull Elem> seq) {
    this.opSet = opSet;
    this.seq = seq;
  }

  private final LinkedBuffer<Tuple2<Elem, BinOpSet.Elem>> opStack = LinkedBuffer.of();
  private final DoubleLinkedBuffer<Elem> prefixes = DoubleLinkedBuffer.of();

  @NotNull public Expr build(@NotNull SourcePos sourcePos) {
    var first = seq.first();
    if (opSet.assocOf(first.asOpDecl()).infix) {
      // + f a b c d
      // \lam _ => _ + f a b c d
      var lhs = new LocalVar(Constants.ANONYMOUS_PREFIX, SourcePos.NONE);
      var lhsElem = new Elem(new Expr.RefExpr(SourcePos.NONE, lhs, "_"), true);
      var lamSeq = seq.prepended(lhsElem);
      return new Expr.LamExpr(sourcePos,
        new Expr.Param(SourcePos.NONE, lhs, true),
        new BinOpParser(opSet, lamSeq).build(sourcePos));
    }

    // insert \app operator between elems
    var seqWithApp = Buffer.<BinOpParser.Elem>create();
    boolean lastIsUsedAsOp = false;
    for (var expr : seq) {
      if (expr.isNotUsedAsOp(opSet)) {
        if (!lastIsUsedAsOp && seqWithApp.isNotEmpty()) seqWithApp.append(Elem.OP_APP);
        lastIsUsedAsOp = false;
      } else {
        lastIsUsedAsOp = true;
      }
      seqWithApp.append(expr);
    }

    // convert infix to prefix
    for (var expr : seqWithApp) {
      if (expr.isNotUsedAsOp(opSet)) prefixes.append(expr);
      else {
        var currentOp = expr.toSetElem(opSet);
        while (opStack.isNotEmpty()) {
          var peek = opStack.peek();
          var cmp = opSet.compare(peek._2, currentOp);
          if (cmp == BinOpSet.PredCmp.Undefined) {
            opSet.reporter().report(new OperatorProblem.AmbiguousPredError(currentOp.name(),
              peek._2.name(),
              peek._1.expr.sourcePos()));
            return new Expr.ErrorExpr(sourcePos, Doc.english("an application"));
          } else if (cmp == BinOpSet.PredCmp.Tighter || cmp == BinOpSet.PredCmp.Equal) {
            var topOp = opStack.pop();
            var appExpr = makeBinApp(topOp._1);
            prefixes.append(new Elem(appExpr, topOp._1.explicit));
          } else break;
        }
        opStack.push(Tuple.of(expr, currentOp));
      }
    }

    while (opStack.isNotEmpty()) {
      var op = opStack.pop();
      var app = makeBinApp(op._1);
      prefixes.append(new Elem(app, op._1.explicit));
    }

    assert prefixes.isNotEmpty();
    if (prefixes.sizeEquals(1)) return prefixes.first().expr;

    // TODO: If this path can't be reached, delete it.
    return new Expr.AppExpr(
      sourcePos,
      prefixes.first().expr,
      prefixes.view().get(1).toNamedArg()
    );
  }

  private Expr.@NotNull AppExpr makeBinApp(@NotNull Elem op) {
    var rhs = prefixes.dequeue();
    var lhs = prefixes.dequeue();
    if (op == Elem.OP_APP) {
      return new Expr.AppExpr(
        union(lhs, rhs),
        lhs.expr,
        rhs.toNamedArg()
      );
    }
    return new Expr.AppExpr(
      computeSourcePos(op.expr.sourcePos(), lhs.expr.sourcePos(), rhs.expr.sourcePos()),
      new Expr.AppExpr(union(op, lhs), op.expr, lhs.toNamedArg()),
      rhs.toNamedArg()
    );
  }

  private @NotNull SourcePos computeSourcePos(@NotNull SourcePos a, @NotNull SourcePos b, @NotNull SourcePos c) {
    return a.union(b).union(c);
  }

  private @NotNull SourcePos union(@NotNull Elem a, @NotNull Elem b) {
    return a.expr.sourcePos().union(b.expr.sourcePos());
  }

  /**
   * something like {@link Arg<Expr>}
   * but only used in binary operator building
   */
  public record Elem(@Nullable String name, @NotNull Expr expr, boolean explicit) {
    private static final Elem OP_APP = new Elem(
      OpDecl.APP_NAME,
      new Expr.ErrorExpr(SourcePos.NONE, Doc.english("fakeApp escaped from BinOpParser")),
      true
    );

    private boolean isBuiltinOp() {
      return this == OP_APP;
    }

    public Elem(@NotNull Expr expr, boolean explicit) {
      this(null, expr, explicit);
    }

    private @Nullable Tuple3<String, @NotNull OpDecl, String> asOpDecl() {
      if (expr instanceof Expr.RefExpr ref
        && ref.resolvedVar() instanceof DefVar<?, ?> defVar
        && defVar.concrete instanceof OpDecl opDecl) {
        return Tuple.of(defVar.name(), opDecl, ref.resolvedFrom());
      }
      return null;
    }

    public boolean isNotUsedAsOp(@NotNull BinOpSet opSet) {
      if (isBuiltinOp()) return false;
      var tryOp = asOpDecl();
      return opSet.isNotUsedAsOperator(tryOp);
    }

    public BinOpSet.@NotNull Elem toSetElem(@NotNull BinOpSet opSet) {
      if (isBuiltinOp()) {
        if (this == OP_APP) return opSet.ensureHasElem(OpDecl.APP_NAME, OpDecl.APP);
        else throw new IllegalStateException("unreachable");
      }
      var tryOp = asOpDecl();
      assert tryOp != null; // should never fail
      return opSet.ensureHasElem(tryOp._1, tryOp._2);
    }

    public @NotNull Arg<Expr.NamedArg> toNamedArg() {
      return new Arg<>(new Expr.NamedArg(name, expr), explicit);
    }
  }
}
