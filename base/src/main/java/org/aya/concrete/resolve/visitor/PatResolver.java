// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.visitor;

import org.aya.api.error.SourcePos;
import org.aya.concrete.Pattern;
import org.aya.concrete.resolve.context.Context;
import org.aya.ref.LocalVar;
import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.tuple.Tuple2;
import org.glavo.kala.value.Ref;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public final class PatResolver implements Pattern.Visitor<Context, Tuple2<Context, Pattern>> {
  public static final @NotNull PatResolver INSTANCE = new PatResolver();

  private PatResolver() {
  }

  public Pattern.Clause matchy(Pattern.@NotNull Clause match, Context context) {
    var ctx = new Ref<>(context);
    var pats = match.patterns().map(pat -> subpatterns(ctx, pat));
    return new Pattern.Clause(match.sourcePos(), pats, match.expr().map(e -> e.resolve(ctx.value)));
  }

  Pattern subpatterns(Ref<Context> ctx, Pattern pat) {
    var res = pat.accept(this, ctx.value);
    ctx.value = res._1;
    return res._2;
  }

  private Context bindAs(LocalVar as, Context ctx, SourcePos sourcePos) {
    return as != null ? ctx.bind(as, sourcePos) : ctx;
  }

  @Contract(value = "_, _ -> fail", pure = true)
  @Override public Tuple2<Context, Pattern> visitCtor(Pattern.@NotNull Ctor ctor, Context context) {
    var newCtx = new Ref<>(context);
    var params = ctor.params().map(p -> subpatterns(newCtx, p));
    var sourcePos = ctor.sourcePos();
    return Tuple.of(
      bindAs(ctor.as(), newCtx.value, sourcePos),
      new Pattern.Ctor(sourcePos, ctor.explicit(), ctor.name(), params, ctor.as()));
  }

  @Override public Tuple2<Context, Pattern> visitTuple(Pattern.@NotNull Tuple tuple, Context context) {
    var newCtx = new Ref<>(context);
    var patterns = tuple.patterns().map(p -> subpatterns(newCtx, p));
    return Tuple.of(
      bindAs(tuple.as(), newCtx.value, tuple.sourcePos()),
      new Pattern.Tuple(tuple.sourcePos(), tuple.explicit(), patterns, tuple.as()));
  }

  @Override public Tuple2<Context, Pattern> visitNumber(Pattern.@NotNull Number number, Context context) {
    return Tuple.of(context, number);
  }

  @Override public Tuple2<Context, Pattern> visitCalmFace(Pattern.@NotNull CalmFace f, Context context) {
    return Tuple.of(context, f);
  }

  @Override public Tuple2<Context, Pattern> visitBind(Pattern.@NotNull Bind bind, Context context) {
    bind.resolved().value = context.getUnqualifiedMaybe(bind.bind().name(), bind.sourcePos());
    return Tuple.of(context.bind(bind.bind(), bind.sourcePos(), var -> false), bind);
  }
}
