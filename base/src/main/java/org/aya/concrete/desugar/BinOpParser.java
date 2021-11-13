// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.desugar;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.Set;
import kala.collection.mutable.*;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.api.ref.DefVar;
import org.aya.api.util.Arg;
import org.aya.concrete.Expr;
import org.aya.concrete.desugar.error.OperatorProblem;
import org.aya.concrete.stmt.Signatured;
import org.aya.generic.Constants;
import org.aya.pretty.doc.Doc;
import org.aya.util.binop.Assoc;
import org.aya.util.binop.BinOpSet;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public final class BinOpParser {
  private final @NotNull AyaBinOpSet opSet;
  private final @NotNull SeqView<@NotNull Elem> seq;

  public BinOpParser(@NotNull AyaBinOpSet opSet, @NotNull SeqView<@NotNull Elem> seq) {
    this.opSet = opSet;
    this.seq = seq;
  }

  private final DynamicLinkedSeq<Tuple2<Elem, BinOpSet.BinOP>> opStack = DynamicLinkedSeq.create();
  private final DynamicDoubleLinkedSeq<Elem> prefixes = DynamicDoubleLinkedSeq.create();
  private final MutableMap<Elem, MutableSet<AppliedSide>> appliedOperands = MutableMap.create();

  @NotNull public Expr build(@NotNull SourcePos sourcePos) {
    // No need to build
    if (seq.sizeEquals(1)) return seq.get(0).expr;
    // BinOP section fast path
    if (seq.sizeEquals(2)) {
      var first = seq.get(0);
      var second = seq.get(1);
      // case 1: `+ f` becomes `\lam _ => _ + f`
      if (opSet.assocOf(first.asOpDecl()).infix && first.argc() == 2)
        return makeSectionApp(sourcePos, first, elem -> new BinOpParser(opSet, seq.prepended(elem)).build(sourcePos));
      // case 2: `f +` becomes `\lam _ => f + _`
      if (opSet.assocOf(second.asOpDecl()).infix && second.argc() == 2)
        return makeSectionApp(sourcePos, second, elem -> new BinOpParser(opSet, seq.appended(elem)).build(sourcePos));
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
      if (expr.isOperand(opSet)) prefixes.append(expr);
      else {
        var currentOp = expr.toSetElem(opSet);
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
              opSet.reporter.report(new OperatorProblem.FixityError(currentOp.name(),
                currentAssoc, top._2.name(), topAssoc, top._1.expr.sourcePos()));
              return new Expr.ErrorExpr(sourcePos, Doc.english("an application"));
            }
            if (topAssoc == Assoc.InfixL) foldLhsFor(expr);
            else break;
          } else if (cmp == BinOpSet.PredCmp.Looser) {
            break;
          } else {
            opSet.reporter.report(new OperatorProblem.AmbiguousPredError(
              currentOp.name(), top._2.name(), top._1.expr.sourcePos()));
            return new Expr.ErrorExpr(sourcePos, Doc.english("an application"));
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

  private @NotNull Seq<Elem> insertApplication(@NotNull SeqView<@NotNull Elem> seq) {
    var seqWithApp = DynamicSeq.<Elem>create();
    var lastIsOperand = true;
    for (var expr : seq) {
      var isOperand = expr.isOperand(opSet);
      if (isOperand && lastIsOperand && seqWithApp.isNotEmpty()) seqWithApp.append(Elem.OP_APP);
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
    int argc = op.argc();
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

  enum AppliedSide {
    Lhs, Rhs
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

    public BinOpSet.@NotNull BinOP toSetElem(@NotNull BinOpSet opSet) {
      if (this == OP_APP) return BinOpSet.APP_ELEM;
      var tryOp = asOpDecl();
      assert tryOp != null; // should never fail
      return opSet.ensureHasElem(tryOp);
    }

    private int argc() {
      if (this == OP_APP) return 2;
      if (this.asOpDecl() instanceof Signatured sig) return countExplicitParam(sig.telescope);
      throw new IllegalArgumentException("not an operator");
    }

    private int countExplicitParam(@NotNull Seq<Expr.Param> telescope) {
      return telescope.view().count(Expr.Param::explicit);
    }

    public @NotNull Arg<Expr.NamedArg> toNamedArg() {
      return new Arg<>(new Expr.NamedArg(name, expr), explicit);
    }
  }
}
