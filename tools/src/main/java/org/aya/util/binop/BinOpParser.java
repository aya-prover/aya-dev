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

public abstract class BinOpParser<OpSet extends BinOpSet, Expr extends SourceNode, Arg> {
  protected final @NotNull OpSet opSet;
  private final @NotNull SeqView<@NotNull Elem<Expr>> seq;

  public BinOpParser(@NotNull OpSet opSet, @NotNull SeqView<@NotNull Elem<Expr>> seq) {
    this.opSet = opSet;
    this.seq = seq;
  }

  private final DynamicLinkedSeq<Tuple2<Elem<Expr>, BinOpSet.BinOP>> opStack = DynamicLinkedSeq.create();
  private final DynamicDoubleLinkedSeq<Elem<Expr>> prefixes = DynamicDoubleLinkedSeq.create();
  private final MutableMap<Elem<Expr>, MutableSet<AppliedSide>> appliedOperands = MutableMap.create();

  /** @implSpec equivalent to <code>new BinOpParser(this.opSet, seq)</code>. */
  protected abstract @NotNull BinOpParser<OpSet, Expr, Arg> replicate(@NotNull SeqView<@NotNull Elem<Expr>> seq);
  /** @implSpec must always return a static instance! */
  protected abstract @NotNull Elem<Expr> appOp();

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

  /**
   * @param pos     the position of the entire expression
   * @param op      the binary operator
   * @param lamBody builds the body of the lambda
   * @implSpec should create a lambda expression with
   * <code>lamBody.apply(lamArg)</code> as its body.
   */
  public abstract @NotNull Expr makeSectionApp(
    @NotNull SourcePos pos, @NotNull Elem<Expr> op,
    @NotNull Function<Elem<Expr>, Expr> lamBody);

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

  private @NotNull Seq<Elem<Expr>> insertApplication() {
    var seqWithApp = DynamicSeq.<Elem<Expr>>create();
    var lastIsOperand = true;
    for (var expr : seq) {
      var isOperand = isOperand(expr, opSet);
      if (isOperand && lastIsOperand && seqWithApp.isNotEmpty()) seqWithApp.append(appOp());
      lastIsOperand = isOperand;
      seqWithApp.append(expr);
    }
    return seqWithApp;
  }

  private void markAppliedOperand(@NotNull Elem<Expr> elem, @NotNull BinOpParser.AppliedSide side) {
    appliedOperands.getOrPut(elem, MutableSet::of).add(side);
  }

  private @NotNull Set<AppliedSide> getAppliedSides(@NotNull Elem<Expr> elem) {
    return appliedOperands.getOrPut(elem, MutableSet::of);
  }

  private void foldLhsFor(@NotNull Elem<Expr> forOp) {
    foldTop();
    markAppliedOperand(forOp, AppliedSide.Lhs);
  }

  private void foldTop() {
    var op = opStack.pop();
    var app = makeBinApp(op._1);
    prefixes.append(new Elem<>(app, op._1.explicit));
  }

  private @NotNull Expr makeBinApp(@NotNull Elem<Expr> op) {
    int argc = argc(op);
    if (argc == 1) {
      var operand = prefixes.dequeue();
      return makeApp(union(operand, op), op.expr, makeArg(operand));
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

  private @NotNull Expr makeBinApp(@NotNull Elem<Expr> op, @NotNull Elem<Expr> rhs, @NotNull Elem<Expr> lhs) {
    if (op == appOp()) return makeApp(union(lhs, rhs), lhs.expr, makeArg(rhs));
    return makeApp(
      union(op, lhs, rhs),
      makeApp(union(op, lhs), op.expr, makeArg(lhs)),
      makeArg(rhs)
    );
  }

  public boolean isOperand(@NotNull Elem<Expr> elem, @NotNull BinOpSet opSet) {
    if (elem == appOp()) return false;
    var tryOp = asOpDecl(elem);
    return opSet.isOperand(tryOp);
  }

  public BinOpSet.@NotNull BinOP toSetElem(@NotNull Elem<Expr> elem, @NotNull BinOpSet opSet) {
    if (elem == appOp()) return BinOpSet.APP_ELEM;
    var tryOp = asOpDecl(elem);
    assert tryOp != null; // should never fail
    return opSet.ensureHasElem(tryOp);
  }

  private int argc(@NotNull Elem<Expr> elem) {
    return elem == appOp() ? 2 : argc(Objects.requireNonNull(asOpDecl(elem)));
  }

  protected abstract @Nullable OpDecl asOpDecl(@NotNull Elem<Expr> elem);
  protected abstract int argc(@NotNull OpDecl decl);
  protected abstract @NotNull Expr makeApp(@NotNull SourcePos sourcePos, @NotNull Expr function, @NotNull Arg arg);
  protected abstract @NotNull Arg makeArg(@NotNull Elem<Expr> elem);

  private @NotNull SourcePos union(@NotNull Elem<Expr> a, @NotNull Elem<Expr> b, @NotNull Elem<Expr> c) {
    return union(a, b).union(c.expr.sourcePos());
  }

  private @NotNull SourcePos union(@NotNull Elem<Expr> a, @NotNull Elem<Expr> b) {
    return a.expr.sourcePos().union(b.expr.sourcePos());
  }

  enum AppliedSide {
    Lhs, Rhs
  }

  public record Elem<Expr extends SourceNode>(@Nullable String name, @NotNull Expr expr, boolean explicit) {
    public Elem(@NotNull Expr expr, boolean explicit) {
      this(null, expr, explicit);
    }
  }
}
