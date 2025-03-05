// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.salt;

import kala.collection.mutable.MutableLinkedList;
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

  @Override
  public Expr apply(SourcePos sourcePos, Expr expr) {
    var shouldCollect = collectNextLayer;
    if (shouldCollect) scopes.push(new HoleCollector());

    Expr result = expr;
    switch (expr) {
      case Expr.LambdaHole() -> {
        var fresh = Constants.randomlyNamed(sourcePos);
        scopes.peek().add(fresh);
        result = new Expr.Ref(fresh);
      }
      case Expr.Lambda lambda -> {
        collectNextLayer = true;
        result = lambda.descent(this);
      }
      default -> {
        collectNextLayer = false;
        result = result.descent(this);
      }
    }

    if (shouldCollect) result = popScopeAndTransform(result);
    return result;
  }

  private @NotNull Expr popScopeAndTransform(Expr from) {
    return Expr.buildLam(SourcePos.NONE, scopes.pop().holes.view(), WithPos.dummy(from)).data();
  }

  private static class HoleCollector {
    @NotNull MutableList<LocalVar> holes = new MutableLinkedList<>();
    public void add(LocalVar var) { holes.append(var); }
  }
}
