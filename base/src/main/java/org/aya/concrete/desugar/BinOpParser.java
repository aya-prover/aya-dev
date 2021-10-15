// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.desugar;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.mutable.Buffer;
import kala.collection.mutable.DoubleLinkedBuffer;
import kala.collection.mutable.LinkedBuffer;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.DefVar;
import org.aya.api.util.Arg;
import org.aya.concrete.Expr;
import org.aya.concrete.desugar.error.OperatorProblem;
import org.aya.concrete.stmt.OpDecl;
import org.aya.pretty.doc.Doc;
import org.aya.util.Constants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

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
    // No need to build
    if (seq.sizeEquals(1)) return seq.get(0).expr;
    // check for BinOP section
    if (seq.sizeEquals(2)) {
      var first = seq.get(0);
      var second = seq.get(1);
      var lam = tryParseSection(sourcePos, first, second);
      if (lam != null) return lam;
    }
    return convertToPrefix(sourcePos);
  }

  public Expr.@Nullable LamExpr tryParseSection(@NotNull SourcePos sourcePos, @NotNull Elem first, @NotNull Elem second) {
    // case 1: `+ f` becomes `\lam _ => _ + f`
    if (opSet.assocOf(first.asOpDecl()).infix) return makeSectionApp(sourcePos, seq, first, SeqView::prepended);
    // case 2: `f +` becomes `\lam _ => f + _`
    if (opSet.assocOf(second.asOpDecl()).infix) return makeSectionApp(sourcePos, seq, second, SeqView::appended);
    return null;
  }

  public Expr.@NotNull LamExpr makeSectionApp(@NotNull SourcePos sourcePos, @NotNull SeqView<Elem> seq, @NotNull Elem op,
                                              @NotNull BiFunction<SeqView<Elem>, Elem, SeqView<Elem>> insertParam) {
    var missing = Constants.randomlyNamed(op.expr.sourcePos());
    var missingElem = new Elem(new Expr.RefExpr(SourcePos.NONE, missing), true);
    var completeSeq = insertParam.apply(seq, missingElem);
    return new Expr.LamExpr(sourcePos,
      new Expr.Param(missing.definition(), missing, true),
      new BinOpParser(opSet, completeSeq).build(sourcePos));
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

  private @NotNull Seq<Elem> insertApplication(@NotNull SeqView<@NotNull Elem> seq) {
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

    private @Nullable OpDecl asOpDecl() {
      if (expr instanceof Expr.RefExpr ref
        && ref.resolvedVar() instanceof DefVar<?, ?> defVar
        && defVar.concrete instanceof OpDecl opDecl) {
        return opDecl;
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
      return opSet.ensureHasElem(tryOp);
    }

    public @NotNull Arg<Expr.NamedArg> toNamedArg() {
      return new Arg<>(new Expr.NamedArg(name, expr), explicit);
    }
  }
}
