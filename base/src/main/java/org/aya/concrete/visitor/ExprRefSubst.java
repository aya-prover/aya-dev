// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.visitor;

import org.aya.api.ref.Var;
import org.aya.concrete.Expr;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record ExprRefSubst(@NotNull MutableHashMap<Var, Var> map) implements ExprFixpoint<Unit> {
  @Override public @NotNull Expr visitRef(@NotNull Expr.RefExpr expr, Unit unit) {
    var v = expr.resolvedVar();
    if (map.containsKey(v)) return new Expr.RefExpr(expr.sourcePos(), map.get(v));
    else return expr;
  }
}
