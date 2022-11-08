// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.binop;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.Set;
import kala.collection.mutable.*;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.util.error.SourceNode;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public abstract class BinOpParser<
  OpSet extends BinOpSet,
  Expr extends SourceNode,
  Elem extends BinOpParser.Elem<Expr>> {
  protected final @NotNull OpSet opSet;
  private final @NotNull SeqView<@NotNull Elem> seq;

  public BinOpParser(@NotNull OpSet opSet, @NotNull SeqView<@NotNull Elem> seq) {
    this.opSet = opSet;
    this.seq = seq;
  }

  private final MutableSinglyLinkedList<Tuple2<Elem, BinOpSet.BinOP>> opStack = MutableSinglyLinkedList.create();
  private final MutableLinkedList<Elem> prefixes = MutableLinkedList.create();
  private final MutableMap<Elem, MutableSet<AppliedSide>> appliedOperands = MutableMap.create();

  /** @implSpec equivalent to <code>new BinOpParser(this.opSet, seq)</code>. */
  protected abstract @NotNull BinOpParser<OpSet, Expr, Elem> replicate(@NotNull SeqView<@NotNull Elem> seq);
  /** @implSpec must always return a static instance! */
  protected abstract @NotNull Elem appOp();

  public @NotNull Expr build(@NotNull SourcePos sourcePos) {
    // No need to build
    if (seq.sizeEquals(1)) return seq.get(0).term();
    // BinOP section fast path
    if (seq.sizeEquals(2)) {
      var first = seq.get(0);
      var second = seq.get(1);
      // case 1: `+ f` becomes `\lam _ => _ + f`
      if (opSet.assocOf(underlyingOpDecl(first)).isBinary())
        return makeSectionApp(sourcePos, first, elem -> replicate(seq.prepended(elem)).build(sourcePos)).term();
      // case 2: `f +` becomes `\lam _ => f + _`
      if (opSet.assocOf(underlyingOpDecl(second)).isBinary())
        return makeSectionApp(sourcePos, second, elem -> replicate(seq.appended(elem)).build(sourcePos)).term();
    }
    return convertToPrefix(sourcePos);
  }

  /**
   * @param pos     the position of the entire expression
   * @param op      the binary operator
   * @param lamBody builds the body of the lambda
   * @implSpec should create a lambda expression with
   * <code>lamBody.apply(lamArg)</code> as its body.
   */
  public abstract @NotNull Elem makeSectionApp(
    @NotNull SourcePos pos, @NotNull Elem op,
    @NotNull Function<Elem, Expr> lamBody);

  private @NotNull Expr convertToPrefix(@NotNull SourcePos sourcePos) {
    for (var expr : insertApplication()) {
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
            if (Assoc.assocAmbitious(topAssoc, currentAssoc)) {
              reportFixityError(topAssoc, currentAssoc, top._2.name(), currentOp.name(), of(top._1));
              return createErrorExpr(sourcePos);
            }
            if (topAssoc.leftAssoc()) foldLhsFor(expr);
            else break;
          } else if (cmp == BinOpSet.PredCmp.Looser) {
            break;
          } else {
            reportAmbiguousPred(currentOp.name(), top._2.name(), of(top._1));
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
    return prefixes.first().term();
  }

  protected abstract void reportAmbiguousPred(String op1, String op2, SourcePos pos);
  protected abstract void reportFixityError(Assoc top, Assoc current, String topOp, String currentOp, SourcePos pos);
  protected abstract @NotNull Expr createErrorExpr(@NotNull SourcePos sourcePos);

  private @NotNull Seq<Elem> insertApplication() {
    var seqWithApp = MutableList.<Elem>create();
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
    prefixes.append(makeBinApp(op._1));
  }

  private @NotNull Elem makeBinApp(@NotNull Elem op) {
    var assoc = toSetElem(op, opSet).assoc();
    if (assoc.isUnary()) {
      var operand = prefixes.dequeue();
      return makeArg(union(operand, op), op.term(), operand, op.explicit());
    } else if (assoc.isBinary()) {
      if (prefixes.sizeGreaterThanOrEquals(2)) {
        var rhs = prefixes.dequeue();
        var lhs = prefixes.dequeue();
        return makeBinApp(op, rhs, lhs);
      } else if (prefixes.sizeEquals(1)) {
        // BinOP section
        var sides = getAppliedSides(op);
        var applied = prefixes.dequeue();
        var side = sides.isEmpty() ? AppliedSide.Lhs : sides.elementAt(0);
        // ^ a unary operator is used as binary section, report here or in type checker?
        return makeSectionApp(union(op, applied), op, elem -> (switch (side) {
          case Lhs -> makeBinApp(op, elem, applied);
          case Rhs -> makeBinApp(op, applied, elem);
        }).term());
      }
    }

    throw new InternalError("unreachable");
  }

  private @NotNull Elem makeBinApp(@NotNull Elem op, @NotNull Elem rhs, @NotNull Elem lhs) {
    var explicit = op.explicit();
    if (op == appOp()) return makeArg(union(lhs, rhs), lhs.term(), rhs, explicit);
    return makeArg(union(op, lhs, rhs), makeArg(union(op, lhs), op.term(), lhs, true).term(), rhs, explicit);
    // ^ `true` above is supposed to be ignored, totally.
  }

  public boolean isOperand(@NotNull Elem elem, @NotNull BinOpSet opSet) {
    if (elem == appOp()) return false;
    var tryOp = underlyingOpDecl(elem);
    return opSet.isOperand(tryOp);
  }

  public BinOpSet.@NotNull BinOP toSetElem(@NotNull Elem elem, @NotNull BinOpSet opSet) {
    if (elem == appOp()) return BinOpSet.APP_ELEM;
    var tryOp = underlyingOpDecl(elem);
    assert tryOp != null; // should never fail
    return opSet.ensureHasElem(tryOp);
  }

  protected abstract @Nullable OpDecl underlyingOpDecl(@NotNull Elem elem);
  protected abstract @NotNull Elem makeArg(@NotNull SourcePos pos, @NotNull Expr func, @NotNull Elem elem, boolean explicit);

  enum AppliedSide {
    Lhs, Rhs
  }

  private @NotNull SourcePos union(@NotNull Elem a, @NotNull Elem b, @NotNull Elem c) {
    return union(a, b).union(of(c));
  }

  private @NotNull SourcePos union(@NotNull Elem a, @NotNull Elem b) {
    return of(a).union(of(b));
  }

  private @NotNull SourcePos of(@NotNull Elem a) {
    return a.term().sourcePos();
  }

  public interface Elem<Expr> {
    @NotNull Expr term();
    boolean explicit();
  }
}
