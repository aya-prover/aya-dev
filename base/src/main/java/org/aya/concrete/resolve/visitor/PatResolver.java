// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve.visitor;

import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.value.Ref;
import org.aya.util.error.SourcePos;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.util.error.WithPos;
import org.aya.concrete.Pattern;
import org.aya.concrete.resolve.context.Context;
import org.aya.concrete.stmt.Decl;
import org.aya.tyck.pat.PatternProblem;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000
 */
public final class PatResolver implements Pattern.Visitor<Context, Tuple2<Context, Pattern>> {
  public static final @NotNull PatResolver INSTANCE = new PatResolver();

  private PatResolver() {
  }

  public Pattern.Clause matchy(
    @NotNull Pattern.Clause match,
    @NotNull Context context,
    @NotNull ExprResolver bodyResolver
  ) {
    var ctx = new Ref<>(context);
    var pats = match.patterns.map(pat -> subpatterns(ctx, pat));
    return new Pattern.Clause(match.sourcePos, pats,
      match.expr.map(e -> e.accept(bodyResolver, ctx.value)));
  }

  @NotNull Pattern subpatterns(Ref<Context> ctx, Pattern pat) {
    var res = pat.accept(this, ctx.value);
    ctx.value = res._1;
    return res._2;
  }

  private Context bindAs(LocalVar as, Context ctx, SourcePos sourcePos) {
    return as != null ? ctx.bind(as, sourcePos) : ctx;
  }

  @Contract(pure = true)
  @Override public Tuple2<Context, Pattern> visitCtor(Pattern.@NotNull Ctor ctor, Context context) {
    var namePos = ctor.resolved().sourcePos();
    var resolution = findPatternDef(context, namePos, ctor.resolved().data().name());
    if (resolution == null) context.reportAndThrow(new PatternProblem.UnknownCtor(ctor));
    var sourcePos = ctor.sourcePos();
    var newCtx = new Ref<>(context);
    var params = ctor.params().map(p -> subpatterns(newCtx, p));
    return Tuple.of(
      bindAs(ctor.as(), newCtx.value, sourcePos),
      new Pattern.Ctor(sourcePos, ctor.explicit(), new WithPos<>(namePos, resolution), params, ctor.as()));
  }

  private @Nullable DefVar<?, ?> findPatternDef(Context context, SourcePos namePos, String name) {
    return context.iterate(c -> {
      var maybe = c.getUnqualifiedLocalMaybe(name, namePos);
      if (!(maybe instanceof DefVar<?, ?> defVar)) return null;
      if (defVar.concrete instanceof Decl.DataCtor) return defVar;
      if (defVar.concrete instanceof Decl.PrimDecl) return defVar;
      return null;
    });
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

  @Override public Tuple2<Context, Pattern> visitAbsurd(Pattern.@NotNull Absurd number, Context context) {
    return Tuple.of(context, number);
  }

  @Override public Tuple2<Context, Pattern> visitCalmFace(Pattern.@NotNull CalmFace f, Context context) {
    return Tuple.of(context, f);
  }

  @Override public Tuple2<Context, Pattern> visitBind(Pattern.@NotNull Bind bind, Context context) {
    var maybe = findPatternDef(context, bind.sourcePos(), bind.bind().name());
    if (maybe != null) return Tuple.of(context, new Pattern.Ctor(bind, maybe));
    else return Tuple.of(context.bind(bind.bind(), bind.sourcePos(), var -> false), bind);
  }
}
