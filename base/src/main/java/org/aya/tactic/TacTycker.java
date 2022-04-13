// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tactic;

import kala.tuple.Unit;
import org.aya.concrete.Expr;
import org.aya.concrete.TacNode;
import org.aya.concrete.visitor.ExprConsumer;
import org.aya.concrete.visitor.ExprOps;
import org.aya.core.term.ErrorTerm;
import org.aya.core.term.Term;
import org.aya.generic.util.InternalException;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.error.TacticProblem.HoleFillerCannotHaveHole;
import org.aya.tyck.error.TacticProblem.HoleFillerNumberMismatch;
import org.aya.tyck.error.TacticProblem.NestedTactic;
import org.aya.tyck.error.TacticProblem.TacHeadCannotBeList;
import org.aya.util.distill.AyaDocile;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TacTycker {
  private final ExprTycker exprTycker;
  private final ExprOps.HoleFiller holeFiller = new ExprOps.HoleFiller();

  public TacTycker(@NotNull ExprTycker exprTycker) {
    this.exprTycker = exprTycker;
  }

  public @NotNull ExprTycker.Result synthesizeTactic(Expr.@NotNull TacExpr tac) {
    return switch (tac.tacNode()) {
      case TacNode.ExprTac exprTac -> exprTycker.synthesize(exprTac.expr());
      case TacNode.ListExprTac listExprTac -> {
        var tacNodes = listExprTac.tacNodes();
        var headTac = tacNodes.first();

        if (headTac instanceof TacNode.ExprTac exprHead) {
          var inferredResult = exprTycker.synthesize(exprHead.expr());
          yield inferredResult.isError() ? inferredResult : exprTycker.inherit(tac, inferredResult.type());
        } else
          yield tacFail(headTac, new TacHeadCannotBeList(listExprTac.sourcePos(), listExprTac)).result;
      }
    };
  }

  public @NotNull ExprTycker.Result inheritTactic(Expr.@NotNull TacExpr tac, @NotNull Term term) {
    var theNested = nestedTacBlock(tac);

    if (theNested != null)
      return tacFail(tac, new NestedTactic(tac.sourcePos(), tac, theNested)).result;

    return inheritTacNode(tac.tacNode(), term).result;
  }

  private @NotNull TacElabResult inheritTacNode(@NotNull TacNode node, Term term) {
    return switch (node) {
      case TacNode.ExprTac exprTac -> inheritExprTac(exprTac, term);
      case TacNode.ListExprTac listExprTac -> inheritListExprTac(listExprTac, term);
    };
  }

  private @NotNull TacElabResult inheritListExprTac(TacNode.@NotNull ListExprTac listExprTac, Term term) {
    TacElabResult result = null;

    var tacNodes = listExprTac.tacNodes();
    var headNode = tacNodes.first();
    var tailNodes = tacNodes.drop(1);

    // enter into a local state
    var parentCtx = exprTycker.localCtx;
    exprTycker.localCtx.deriveMap();

    if (headNode instanceof TacNode.ExprTac headTac) {
      var exprToElab = headTac.expr();
      var tacHeadResult = exprTycker.inherit(exprToElab, term); // tyck this expr to insert all metas
      var headTerm = tacHeadResult.wellTyped();

      if (headTerm instanceof ErrorTerm errorTerm)
        return tacPropagateError(headTac, errorTerm, tacHeadResult);

      var metas = headTerm.allMetas();

      // We can check the remaining nodes against the meta type, but the problem is that the later expected types might change,
      // so we need to instantiate the meta and tyck again.
      // We should refill the final, filled concrete expr and type check it against the goal type.
      while (metas.isNotEmpty()) {
        var metaSize = metas.size();
        if (tailNodes.size() == metaSize) {
          var firstMeta = metas.first();
          var firstNode = tailNodes.first();

          if (firstMeta.result == null) throw new InternalException("Meta " + firstMeta + " has unknown type");
          var fillingElabResult = inheritTacNode(firstNode, firstMeta.result);
          var filling = fillingElabResult.elaborated;

          // propagate error immediately
          if (filling instanceof Expr.ErrorExpr) return fillingElabResult;

          tailNodes = tailNodes.drop(1);
          exprToElab = holeFiller.fill(exprToElab, filling);
          headTerm = exprTycker.inherit(exprToElab, term).wellTyped();
          metas = headTerm.allMetas();

          if (metas.size() >= metaSize) throw new InternalException("Meta is not solved after elaborating tactic");
        } else {
          result = tacFail(exprToElab,
            new HoleFillerNumberMismatch(listExprTac.sourcePos(), metaSize, tailNodes.size()));
          break;
        }
      }

      // null means that there is no meta to solve
      if (result == null) result = new TacElabResult(exprToElab,
        new ExprTycker.Result(exprTycker.inherit(exprToElab, term).wellTyped(), term));

    } else {
      result = tacFail(headNode, new TacHeadCannotBeList(listExprTac.sourcePos(), listExprTac));
    }

    exprTycker.localCtx = parentCtx; // revert to the original state
    return result;
  }

  private @NotNull TacElabResult inheritExprTac(TacNode.@NotNull ExprTac exprTac, @NotNull Term term) {
    var result = exprTycker.inherit(exprTac.expr(), term);
    var metaSize = result.wellTyped().allMetas().size();

    if (result.wellTyped() instanceof ErrorTerm errorTerm)
      return tacPropagateError(exprTac, errorTerm, result);
    else if (metaSize != 0)
      return tacFail(exprTac.expr(), new HoleFillerCannotHaveHole(exprTac.sourcePos(), exprTac));

    return new TacElabResult(exprTac.expr(), result);
  }

  /**
   * Fail and report error
   *
   * @param tacNode the {@link TacNode} that fails to be elaborated
   * @param problem the {@link Problem} that occurred
   * @return a {@link TacElabResult} that represents error
   */
  private @NotNull TacElabResult tacFail(@NotNull TacNode tacNode, @NotNull Problem problem) {
    var errorTyckResult = exprTycker.fail(tacNode, problem);
    return TacElabResult.error(tacNode.sourcePos(), tacNode, errorTyckResult);
  }

  private @NotNull TacElabResult tacFail(@NotNull Expr exprToElab, @NotNull Problem problem) {
    var errorTyckResult = exprTycker.fail(exprToElab, problem);
    return TacElabResult.error(exprToElab.sourcePos(), exprToElab, errorTyckResult);
  }

  private @NotNull TacElabResult tacPropagateError(@NotNull TacNode tacNode,
                                                   @NotNull ErrorTerm errorTerm,
                                                   @NotNull ExprTycker.Result errorResult) {
    return TacElabResult.error(tacNode.sourcePos(), errorTerm.description(), errorResult);
  }

  private @Nullable Expr.TacExpr nestedTacBlock(@NotNull Expr.TacExpr tac) {
    var nestChecker = new ExprConsumer<Unit>() {
      Expr.TacExpr theNested = null;

      @Override public Unit visitTac(@NotNull Expr.TacExpr tactic, Unit unit) {
        if (tactic != tac) {
          theNested = tactic;
          return unit;
        }
        return ExprConsumer.super.visitTac(tactic, unit);
      }
    };

    tac.accept(nestChecker, Unit.unit());
    return nestChecker.theNested;
  }


  /**
   * Tactic elaboration result that contains expr with filled holes
   *
   * @param elaborated the {@link Expr} being elaborated
   * @param result     the {@link ExprTycker.Result} after checking
   * @author Luna
   */
  private record TacElabResult(@NotNull Expr elaborated, @NotNull ExprTycker.Result result) {
    @Contract("_, _, _ -> new")
    public static @NotNull TacElabResult error(@NotNull SourcePos sourcePos,
                                               @NotNull AyaDocile description,
                                               @NotNull ExprTycker.Result errorTyckResult) {
      return new TacElabResult(new Expr.ErrorExpr(sourcePos, description), errorTyckResult);
    }
  }
}
