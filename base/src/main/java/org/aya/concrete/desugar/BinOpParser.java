// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.desugar;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.Set;
import kala.collection.mutable.*;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.api.util.Arg;
import org.aya.concrete.Expr;
import org.aya.generic.Constants;
import org.aya.util.binop.Assoc;
import org.aya.util.binop.BinOpSet;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public abstract class BinOpParser<OpSet extends BinOpSet> {
  protected final @NotNull OpSet opSet;
  private final @NotNull SeqView<@NotNull Elem> seq;

  public BinOpParser(@NotNull OpSet opSet, @NotNull SeqView<@NotNull Elem> seq) {
    this.opSet = opSet;
    this.seq = seq;
  }

  private final DynamicLinkedSeq<Tuple2<Elem, BinOpSet.BinOP>> opStack = DynamicLinkedSeq.create();
  private final DynamicDoubleLinkedSeq<Elem> prefixes = DynamicDoubleLinkedSeq.create();
  private final MutableMap<Elem, MutableSet<AppliedSide>> appliedOperands = MutableMap.create();

  /** @implSpec equivalent to <code>new BinOpParser(this.opSet, seq)</code>. */
  protected abstract @NotNull BinOpParser<OpSet> replicate(@NotNull SeqView<@NotNull Elem> seq);
  /** @implSpec must always return a static instance! */
  protected abstract @NotNull Elem appOp();

  public @NotNull Expr build(@NotNull SourcePos sourcePos) {
    // No need to build
    if (seq.sizeEquals(1)) return seq.get(0).expr;
    // BinOP section fast path
    if (seq.sizeEquals(2)) {
      var first = seq.get(0);
      var second = seq.get(1);
      // case 1: `+ f` becomes `\lam _ => _ + f`
      if (opSet.assocOf(asOpDecl(first)).infix && argc(first) == 2)
        return makeSectionApp(sourcePos, first, elem -> replicate(seq.prepended(elem)).build(sourcePos));
      // case 2: `f +` becomes `\lam _ => f + _`
      if (opSet.assocOf(asOpDecl(second)).infix && argc(second) == 2)
        return makeSectionApp(sourcePos, second, elem -> replicate(seq.appended(elem)).build(sourcePos));
    }
    return convertToPrefix(sourcePos);
  }

  public Expr.@NotNull LamExpr makeSectionApp(@NotNull SourcePos sourcePos,
                                              @NotNull Elem op,
                                              @NotNull Function<Elem, Expr> lamBody) {
    var missing = Constants.randomlyNamed(op.expr.sourcePos());
    var missingElem = new Elem(new Expr.RefExpr(SourcePos.NONE, missing), true);
    var missingParam = new Expr.Param(missing.definition(), missing, true);
    return new Expr.LamExpr(sourcePos, missingParam, lamBody.apply(missingElem));
  }

  private @NotNull Expr convertToPrefix(@NotNull SourcePos sourcePos) {
    for (var expr : insertApplication(seq)) {
      if (isOperand(expr, opSet)) prefixes.append(expr);
      else {
        var currentOp = toSetElem(expr, opSet);
        while (opStack.isNotEmpty()) {
          var top = opStack.peek();
          var cmp = opSet.compare(top._2, currentOp);
          if (cmp == BinOpSet.PredCmp.Tighter) foldLhsFor(expr);
          else if (cmp == BinOpSet.PredCmp.Equal) {
            // associativity should be specified to both left/right when their share
            // the same precedence. Or a parse error should be reported.
            var topAssoc = top._2.assoc();
            var currentAssoc = currentOp.assoc();
            if (topAssoc != currentAssoc || topAssoc == Assoc.Infix) {
              reportFixityError(topAssoc, currentAssoc, top._2.name(), currentOp.name(), top._1.expr.sourcePos());
              return createErrorExpr(sourcePos);
            }
            if (topAssoc == Assoc.InfixL) foldLhsFor(expr);
            else break;
          } else if (cmp == BinOpSet.PredCmp.Looser) {
            break;
          } else {
            reportAmbiguousPred(currentOp.name(), top._2.name(), top._1.expr.sourcePos());
            return createErrorExpr(sourcePos);
          }
        }
        opStack.push(Tuple.of(expr, currentOp));
      }
    }

    while (opStack.isNotEmpty()) {
      foldTop();
      if (opStack.isNotEmpty()) markAppliedOperand(opStack.peek()._1, AppliedSide.Rhs);
    }

    assert prefixes.sizeEquals(1);
    return prefixes.first().expr;
  }

  protected abstract void reportAmbiguousPred(String op1, String op2, SourcePos pos);
  protected abstract void reportFixityError(Assoc topAssoc, Assoc currentAssoc, String op2, String op1, SourcePos pos);
  protected abstract @NotNull Expr createErrorExpr(@NotNull SourcePos sourcePos);

  private @NotNull Seq<Elem> insertApplication(@NotNull SeqView<@NotNull Elem> seq) {
    var seqWithApp = DynamicSeq.<Elem>create();
    var lastIsOperand = true;
    for (var expr : seq) {
      var isOperand = isOperand(expr, opSet);
      if (isOperand && lastIsOperand && seqWithApp.isNotEmpty()) seqWithApp.append(appOp());
      lastIsOperand = isOperand;
      seqWithApp.append(expr);
    }
    return seqWithApp;
  }

  private void markAppliedOperand(@NotNull Elem elem, @NotNull BinOpParser.AppliedSide side) {
    appliedOperands.getOrPut(elem, MutableSet::of).add(side);
  }

  private @NotNull Set<AppliedSide> getAppliedSides(@NotNull Elem elem) {
    return appliedOperands.getOrPut(elem, MutableSet::of);
  }

  private void foldLhsFor(@NotNull Elem forOp) {
    foldTop();
    markAppliedOperand(forOp, AppliedSide.Lhs);
  }

  private void foldTop() {
    var op = opStack.pop();
    var app = makeBinApp(op._1);
    prefixes.append(new Elem(app, op._1.explicit));
  }

  private @NotNull Expr makeBinApp(@NotNull Elem op) {
    int argc = argc(op);
    if (argc == 1) {
      var operand = prefixes.dequeue();
      return new Expr.AppExpr(union(operand, op), op.expr, operand.toNamedArg());
    } else if (argc == 2) {
      if (prefixes.sizeGreaterThanOrEquals(2)) {
        var rhs = prefixes.dequeue();
        var lhs = prefixes.dequeue();
        return makeBinApp(op, rhs, lhs);
      } else if (prefixes.sizeEquals(1)) {
        // BinOP section
        var sides = getAppliedSides(op);
        var applied = prefixes.dequeue();
        var side = sides.elementAt(0);
        return makeSectionApp(union(op, applied), op, elem -> switch (side) {
          case Lhs -> makeBinApp(op, elem, applied);
          case Rhs -> makeBinApp(op, applied, elem);
        });
      }
    }

    throw new UnsupportedOperationException("TODO?");
  }

  private @NotNull Expr makeBinApp(@NotNull Elem op, @NotNull Elem rhs, @NotNull Elem lhs) {
    if (op == appOp()) return new Expr.AppExpr(
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

  public boolean isOperand(@NotNull Elem elem, @NotNull BinOpSet opSet) {
    if (elem == appOp()) return false;
    var tryOp = asOpDecl(elem);
    return opSet.isOperand(tryOp);
  }

  public BinOpSet.@NotNull BinOP toSetElem(@NotNull Elem elem, @NotNull BinOpSet opSet) {
    if (elem == appOp()) return BinOpSet.APP_ELEM;
    var tryOp = asOpDecl(elem);
    assert tryOp != null; // should never fail
    return opSet.ensureHasElem(tryOp);
  }

  protected @Nullable abstract OpDecl asOpDecl(@NotNull Elem elem);
  protected abstract int argc(@NotNull Elem elem);

  private @NotNull SourcePos union(@NotNull Elem a, @NotNull Elem b, @NotNull Elem c) {
    return union(a, b).union(c.expr.sourcePos());
  }

  private @NotNull SourcePos union(@NotNull Elem a, @NotNull Elem b) {
    return a.expr.sourcePos().union(b.expr.sourcePos());
  }

  enum AppliedSide {
    Lhs, Rhs
  }

  /**
   * something like {@link Arg<Expr>}
   * but only used in binary operator building
   */
  public record Elem(@Nullable String name, @NotNull Expr expr, boolean explicit) {
    public Elem(@NotNull Expr expr, boolean explicit) {
      this(null, expr, explicit);
    }

    public @NotNull Arg<Expr.NamedArg> toNamedArg() {
      return new Arg<>(new Expr.NamedArg(name, expr), explicit);
    }
  }
}
