// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.visitor;

import asia.kala.control.Option;
import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.Expr;
import org.mzi.concrete.resolve.context.Context;
import org.mzi.concrete.resolve.context.SimpleContext;
import org.mzi.concrete.visitor.ExprFixpoint;
import org.mzi.generic.Tele;

/**
 * Resolves bindings.
 * @author re-xyr
 */
public final class ExprResolveFixpoint implements ExprFixpoint<Context> {
  @Override public @NotNull Tele<Expr> visitNamed(@NotNull Tele.NamedTele<Expr> named, Context ctx) {
    ctx.putLocal(named.ref().name(), named.ref());
    Option.of(named.next()).forEach(next -> next.accept(this, ctx));
    return named;
  }

  @Override public @NotNull Tele<Expr> visitTyped(@NotNull Tele.TypedTele<Expr> typed, Context ctx) {
    ctx.putLocal(typed.ref().name(), typed.ref());
    Option.of(typed.next()).forEach(next -> next.accept(this, ctx));
    return typed;
  }

  @Override public @NotNull Expr visitUnresolved(@NotNull Expr.UnresolvedExpr expr, Context ctx) {
    return new Expr.RefExpr(expr.sourcePos(), Option.of(ctx.get(expr.name()))
      // TODO[xyr]: report instead of throw
      .getOrThrowException(new IllegalStateException("reference to non-existing variable `" + expr.name() + "`")));
  }

  @Override public @NotNull Expr visitLam(@NotNull Expr.LamExpr expr, Context ctx) {
    var local = new SimpleContext();
    local.setSuperContext(ctx);
    expr.tele().accept(this, local);
    var body = expr.body().accept(this, local);
    return new Expr.LamExpr(expr.sourcePos(), expr.tele(), body);
  }

  @Override public @NotNull Expr visitDT(@NotNull Expr.DTExpr expr, Context ctx) {
    var local = new SimpleContext();
    local.setSuperContext(ctx);
    expr.tele().accept(this, local);
    var last = expr.last().accept(this, local);
    return new Expr.LamExpr(expr.sourcePos(), expr.tele(), last);
  }
}
