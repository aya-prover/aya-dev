// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.visitor;

import org.aya.concrete.Expr;
import org.aya.concrete.resolve.context.Context;
import org.aya.generic.Pat;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.tuple.Tuple2;
import org.glavo.kala.value.Ref;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public final class PatResolver implements
  Pat.Clause.Visitor<Expr, Context, Pat.Clause<Expr>>,
  Pat.Visitor<Expr, Context, Tuple2<Context, Pat<Expr>>> {
  public static final @NotNull PatResolver INSTANCE = new PatResolver();

  private PatResolver() {
  }

  @Override public Pat.Clause<Expr> visitMatch(Pat.Clause.@NotNull Match<Expr> match, Context context) {
    var ctx = new Ref<>(context);
    var pats = match.patterns().stream().sequential().map(pat -> {
      var res = pat.accept(this, ctx.value);
      ctx.value = res._1;
      return res._2;
    }).collect(Buffer.factory());
    return new Pat.Clause.Match<>(pats, match.expr().resolve(ctx.value));
  }

  @Override public Pat.Clause<Expr> visitAbsurd(Pat.Clause.@NotNull Absurd<Expr> absurd, Context context) {
    return absurd;
  }

  @Override public Tuple2<Context, Pat<Expr>> visitAtomic(Pat.@NotNull Atomic<Expr> atomic, Context context) {
    throw new UnsupportedOperationException();
  }

  @Override public Tuple2<Context, Pat<Expr>> visitCtor(Pat.@NotNull Ctor<Expr> ctor, Context context) {
    throw new UnsupportedOperationException();
  }

  @Override public Tuple2<Context, Pat<Expr>> visitUnresolved(Pat.@NotNull Unresolved<Expr> unr, Context context) {
    throw new UnsupportedOperationException();
  }
}
