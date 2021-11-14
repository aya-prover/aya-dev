// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.binop;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.Set;
import kala.collection.mutable.*;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

public abstract class BinOpParser<
  OpSet extends BinOpSet,
  Expr extends SourceNode,
  Arg extends BinOpParser.Elem<Expr>> {
  protected final @NotNull OpSet opSet;
  private final @NotNull SeqView<@NotNull Arg> seq;

  public BinOpParser(@NotNull OpSet opSet, @NotNull SeqView<@NotNull Arg> seq) {
    this.opSet = opSet;
    this.seq = seq;
  }

  private final DynamicLinkedSeq<Tuple2<Arg, BinOpSet.BinOP>> opStack = DynamicLinkedSeq.create();
  private final DynamicDoubleLinkedSeq<Arg> prefixes = DynamicDoubleLinkedSeq.create();
  private final MutableMap<Arg, MutableSet<AppliedSide>> appliedOperands = MutableMap.create();

  /** @implSpec equivalent to <code>new BinOpParser(this.opSet, seq)</code>. */
  protected abstract @NotNull BinOpParser<OpSet, Expr, Arg> replicate(@NotNull SeqView<@NotNull Arg> seq);
  /** @implSpec must always return a static instance! */
  protected abstract @NotNull Arg appOp();

  public @NotNull Expr build(@NotNull SourcePos sourcePos) {
    // No need to build
    if (seq.sizeEquals(1)) return seq.get(0).expr();
    // BinOP section fast path
    if (seq.sizeEquals(2)) {
      var first = seq.get(0);
      var second = seq.get(1);
      // case 1: `+ f` becomes `\lam _ => _ + f`
      if (opSet.assocOf(underlyingOpDecl(first)).infix && argc(first) == 2)
        return makeSectionApp(sourcePos, first, elem -> replicate(seq.prepended(elem)).build(sourcePos)).expr();
      // case 2: `f +` becomes `\lam _ => f + _`
      if (opSet.assocOf(underlyingOpDecl(second)).infix && argc(second) == 2)
        return makeSectionApp(sourcePos, second, elem -> replicate(seq.appended(elem)).build(sourcePos)).expr();
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
  public abstract @NotNull Arg makeSectionApp(
    @NotNull SourcePos pos, @NotNull Arg op,
    @NotNull Function<Arg, Expr> lamBody);

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
            if (topAssoc != currentAssoc || topAssoc == Assoc.Infix) {
              reportFixityError(topAssoc, currentAssoc, top._2.name(), currentOp.name(), top._1.sourcePos());
              return createErrorExpr(sourcePos);
            }
            if (topAssoc == Assoc.InfixL) foldLhsFor(expr);
            else break;
          } else if (cmp == BinOpSet.PredCmp.Looser) {
            break;
          } else {
            reportAmbiguousPred(currentOp.name(), top._2.name(), top._1.sourcePos());
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
    return prefixes.first().expr();
  }

  protected abstract void reportAmbiguousPred(String op1, String op2, SourcePos pos);
  protected abstract void reportFixityError(Assoc topAssoc, Assoc currentAssoc, String op2, String op1, SourcePos pos);
  protected abstract @NotNull Expr createErrorExpr(@NotNull SourcePos sourcePos);

  private @NotNull Seq<Arg> insertApplication() {
    var seqWithApp = DynamicSeq.<Arg>create();
    var lastIsOperand = true;
    for (var expr : seq) {
      var isOperand = isOperand(expr, opSet);
      if (isOperand && lastIsOperand && seqWithApp.isNotEmpty()) seqWithApp.append(appOp());
      lastIsOperand = isOperand;
      seqWithApp.append(expr);
    }
    return seqWithApp;
  }

  private void markAppliedOperand(@NotNull Arg elem, @NotNull BinOpParser.AppliedSide side) {
    appliedOperands.getOrPut(elem, MutableSet::of).add(side);
  }

  private @NotNull Set<AppliedSide> getAppliedSides(@NotNull Arg elem) {
    return appliedOperands.getOrPut(elem, MutableSet::of);
  }

  private void foldLhsFor(@NotNull Arg forOp) {
    foldTop();
    markAppliedOperand(forOp, AppliedSide.Lhs);
  }

  private void foldTop() {
    var op = opStack.pop();
    prefixes.append(makeBinApp(op._1));
  }

  private @NotNull Arg makeBinApp(@NotNull Arg op) {
    int argc = argc(op);
    if (argc == 1) {
      var operand = prefixes.dequeue();
      return makeArg(union(operand, op), op.expr(), operand, op.explicit());
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
        return makeSectionApp(union(op, applied), op, elem -> (switch (side) {
          case Lhs -> makeBinApp(op, elem, applied);
          case Rhs -> makeBinApp(op, applied, elem);
        }).expr());
      }
    }

    throw new UnsupportedOperationException("TODO?");
  }

  private @NotNull Arg makeBinApp(@NotNull Arg op, @NotNull Arg rhs, @NotNull Arg lhs) {
    var explicit = op.explicit();
    if (op == appOp()) return makeArg(union(lhs, rhs), lhs.expr(), rhs, explicit);
    return makeArg(union(op, lhs, rhs), makeArg(union(op, lhs), op.expr(), lhs, true).expr(), rhs, explicit);
    // ^ `true` above is supposed to be ignored, totally.
  }

  public boolean isOperand(@NotNull Arg elem, @NotNull BinOpSet opSet) {
    if (elem == appOp()) return false;
    var tryOp = underlyingOpDecl(elem);
    return opSet.isOperand(tryOp);
  }

  public BinOpSet.@NotNull BinOP toSetElem(@NotNull Arg elem, @NotNull BinOpSet opSet) {
    if (elem == appOp()) return BinOpSet.APP_ELEM;
    var tryOp = underlyingOpDecl(elem);
    assert tryOp != null; // should never fail
    return opSet.ensureHasElem(tryOp);
  }

  private int argc(@NotNull Arg elem) {
    return elem == appOp() ? 2 : argc(Objects.requireNonNull(underlyingOpDecl(elem)));
  }

  protected abstract @Nullable OpDecl underlyingOpDecl(@NotNull Arg elem);
  protected abstract int argc(@NotNull OpDecl decl);
  protected abstract @NotNull Arg makeArg(@NotNull SourcePos pos, @NotNull Expr func, @NotNull Arg arg, boolean explicit);

  private @NotNull SourcePos union(@NotNull Arg a, @NotNull Arg b, @NotNull Arg c) {
    return union(a, b).union(c.sourcePos());
  }

  private @NotNull SourcePos union(@NotNull Arg a, @NotNull Arg b) {
    return a.sourcePos().union(b.sourcePos());
  }

  enum AppliedSide {
    Lhs, Rhs
  }

  public interface Elem<Expr extends SourceNode> extends SourceNode {
    @NotNull Expr expr();
    boolean explicit();
    @Override default @NotNull SourcePos sourcePos() {
      return expr().sourcePos();
    }
  }
}
