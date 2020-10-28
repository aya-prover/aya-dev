// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.visitor;

import asia.kala.collection.Seq;
import asia.kala.collection.mutable.Buffer;
import asia.kala.collection.mutable.LinkedBuffer;
import asia.kala.collection.mutable.MutableHashMap;
import asia.kala.collection.mutable.MutableMap;
import asia.kala.control.Option;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Var;
import org.mzi.concrete.Expr;
import org.mzi.generic.Tele;

/**
 * Resolves bindings.
 * @author re-xyr
 */
public final class ResolveFixpoint implements ExprFixpoint<Buffer<@NotNull String>> {
  private final @NotNull MutableMap<String, Buffer<Var>> context = new MutableHashMap<>();

  private void addToContext(String name, Var ref) {
    if (context.containsKey(name)) context.get(name).prepend(ref);
    else context.put(name, LinkedBuffer.of(ref));
  }

  private @NotNull Var lookupInContext(String name) {
    return Option.of(context.get(name)).map(Seq::first)
      .getOrThrowException(new IllegalStateException("non-existing reference to `" + name + "`")); // FIXME[xyr]: Should report to an error reporter, instead of throw
  }

  private void dropFromContext(String name) {
    if (context.containsKey(name)) {
      var stack = context.get(name);
      stack.removeAt(0);
      if (stack.isEmpty()) context.remove(name);
    } else throw new IllegalStateException("trying to remove non-existing reference to `" + name + "`"); // Should not happen
  }

  @Override public @NotNull Tele<Expr> visitNamed(@NotNull Tele.NamedTele<Expr> named, Buffer<@NotNull String> toBeDropped) {
    addToContext(named.ref().name(), named.ref());
    toBeDropped.prepend(named.ref().name());
    Option.of(named.next()).forEach(next -> next.accept(this, toBeDropped));
    return named;
  }

  @Override public @NotNull Tele<Expr> visitTyped(@NotNull Tele.TypedTele<Expr> typed, Buffer<@NotNull String> toBeDropped) {
    addToContext(typed.ref().name(), typed.ref());
    toBeDropped.prepend(typed.ref().name());
    Option.of(typed.next()).forEach(next -> next.accept(this, toBeDropped));
    return typed;
  }

  @Override public @NotNull Expr visitUnresolved(@NotNull Expr.UnresolvedExpr expr, Buffer<@NotNull String> toBeDropped) {
    return new Expr.RefExpr(expr.sourcePos(), lookupInContext(expr.name()));
  }

  @Override public @NotNull Expr visitLam(@NotNull Expr.LamExpr expr, Buffer<@NotNull String> useless) {
    var toBeDropped = new LinkedBuffer<String>();
    expr.tele().accept(this, toBeDropped);
    var body = expr.body().accept(this, null);
    toBeDropped.forEach(this::dropFromContext);
    return new Expr.LamExpr(expr.sourcePos(), expr.tele(), body);
  }

  @Override public @NotNull Expr visitDT(@NotNull Expr.DTExpr expr, Buffer<@NotNull String> useless) {
    var toBeDropped = new LinkedBuffer<String>();
    expr.tele().accept(this, toBeDropped);
    var last = expr.last().accept(this, null);
    toBeDropped.forEach(this::dropFromContext);
    return new Expr.DTExpr(expr.sourcePos(), expr.tele(), last, expr.kind());
  }
}
