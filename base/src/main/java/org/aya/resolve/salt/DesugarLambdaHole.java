// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.salt;

import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableStack;
import org.aya.generic.Constants;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.position.PosedUnaryOperator;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
import org.jetbrains.annotations.NotNull;

public final class DesugarLambdaHole implements PosedUnaryOperator<Expr> {
  private final MutableStack<HoleCollector> scopes = MutableStack.create();
  private boolean collectNextLayer = true;

  @Override public Expr apply(SourcePos sourcePos, Expr expr) {
    var shouldCollect = collectNextLayer;
    if (shouldCollect) scopes.push(new HoleCollector());

    Expr result = expr;
    switch (expr) {
      // For lambda holes, add to the most recent scope.
      case Expr.LambdaHole _ -> {
        var fresh = Constants.randomlyNamed(sourcePos);
        scopes.peek().add(fresh);
        result = new Expr.Ref(fresh);
      }
      // Match requires specialized handling as its discriminant and clauses have different scopes.
      case Expr.Match(var discrs, var clauses, var returns) -> {
        // wrong inspection
        //noinspection UnusedAssignment
        collectNextLayer = false;
        var mappedDiscrs = discrs.map(x -> x.descent(this));
        collectNextLayer = true;
        var mappedClauses = clauses.map(x -> x.descent(this, PosedUnaryOperator.identity()));
        var mappedExprs = returns == null ? null : returns.descent(this);
        result = new Expr.Match(mappedDiscrs, mappedClauses, mappedExprs);
      }

      case Expr.Lambda _ -> {
        collectNextLayer = true;
        result = expr.descent(this);
      }
      default -> {
        collectNextLayer = false;
        result = result.descent(this);
      }
    }

    if (shouldCollect) result = popScopeAndTransform(sourcePos, result);
    return result;
  }

  private @NotNull Expr popScopeAndTransform(SourcePos sourcePos, Expr from) {
    return Expr.buildLam(sourcePos, scopes.pop().holes.view().map(Expr.UntypedParam::dummy), new WithPos<>(sourcePos, from)).data();
  }

  private static class HoleCollector {
    @NotNull MutableList<LocalVar> holes = MutableList.create();
    public void add(LocalVar var) { holes.append(var); }
  }
}
