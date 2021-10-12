// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.desugar;

import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.mutable.Buffer;
import kala.collection.mutable.DoubleLinkedBuffer;
import kala.collection.mutable.LinkedBuffer;
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
    // check for BinOP section
    if (opSet.assocOf(first.asOpDecl()).infix) {
      // case 1: `+ f` becomes `\lam _ => _ + f`
      var lhs = new LocalVar(Constants.ANONYMOUS_PREFIX, SourcePos.NONE);
      var lhsElem = new Elem(new Expr.RefExpr(SourcePos.NONE, lhs, "_"), true);
      var lamSeq = seq.prepended(lhsElem);
      return new Expr.LamExpr(sourcePos,
        new Expr.Param(SourcePos.NONE, lhs, true),
        new BinOpParser(opSet, lamSeq).build(sourcePos));
      // TODO: case 2: `f +` becomes `\lam _ => f + _`
    }
    return convertToPrefix(sourcePos);
  }

  private @NotNull Expr convertToPrefix(@NotNull SourcePos sourcePos) {
    for (var expr : insertApplication(seq)) {
      if (expr.isOperand(opSet)) prefixes.append(expr);
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

    assert prefixes.sizeEquals(1);
    return prefixes.first().expr;
  }

  private @NotNull SeqLike<Elem> insertApplication(@NotNull SeqView<@NotNull Elem> seq) {
    var seqWithApp = Buffer.<Elem>create();
    var lastIsOperand = true;
    for (var expr : seq) {
      var isOperand = expr.isOperand(opSet);
      if (isOperand && lastIsOperand && seqWithApp.isNotEmpty()) seqWithApp.append(Elem.OP_APP);
      lastIsOperand = isOperand;
      seqWithApp.append(expr);
    }
    return seqWithApp;
  }

  private Expr.@NotNull AppExpr makeBinApp(@NotNull Elem op) {
    var rhs = prefixes.dequeue();
    var lhs = prefixes.dequeue();
    if (op == Elem.OP_APP) return new Expr.AppExpr(
      union(lhs, rhs),
      lhs.expr,
      rhs.toNamedArg()
    );
    return new Expr.AppExpr(
      union(op, lhs, rhs),
      new Expr.AppExpr(union(op, lhs), op.expr, lhs.toNamedArg()),
      rhs.toNamedArg()
    );
  }

  private @NotNull SourcePos union(@NotNull Elem a, @NotNull Elem b, @NotNull Elem c) {
    return union(a, b).union(c.expr.sourcePos());
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
      BinOpSet.APP_ELEM.name(),
      new Expr.ErrorExpr(SourcePos.NONE, Doc.english("fakeApp escaped from BinOpParser")),
      true
    );

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

    public boolean isOperand(@NotNull BinOpSet opSet) {
      if (this == OP_APP) return false;
      var tryOp = asOpDecl();
      return opSet.isOperand(tryOp);
    }

    public BinOpSet.@NotNull Elem toSetElem(@NotNull BinOpSet opSet) {
      if (this == OP_APP) return BinOpSet.APP_ELEM;
      var tryOp = asOpDecl();
      assert tryOp != null; // should never fail
      return opSet.ensureHasElem(tryOp._1, tryOp._2);
    }

    public @NotNull Arg<Expr.NamedArg> toNamedArg() {
      return new Arg<>(new Expr.NamedArg(name, expr), explicit);
    }
  }
}
