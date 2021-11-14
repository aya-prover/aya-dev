// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve.visitor;

import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.value.Ref;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.concrete.Pattern;
import org.aya.concrete.resolve.context.Context;
import org.aya.concrete.stmt.Decl;
import org.aya.tyck.pat.PatternProblem;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000
 */
public final class PatResolver {
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
    var res = resolve(pat, ctx.value);
    ctx.value = res._1;
    return res._2;
  }

  private Context bindAs(LocalVar as, Context ctx, SourcePos sourcePos) {
    return as != null ? ctx.bind(as, sourcePos) : ctx;
  }

  private Tuple2<Context, Pattern> resolve(@NotNull Pattern pattern, Context context) {
    return switch (pattern) {
      case Pattern.Ctor ctor -> {
        var namePos = ctor.resolved().sourcePos();
        var resolution = findPatternDef(context, namePos, ctor.resolved().data().name());
        if (resolution == null) context.reportAndThrow(new PatternProblem.UnknownCtor(ctor));
        var sourcePos = ctor.sourcePos();
        var newCtx = new Ref<>(context);
        var params = ctor.params().map(p -> subpatterns(newCtx, p));
        yield Tuple.of(
          bindAs(ctor.as(), newCtx.value, sourcePos),
          new Pattern.Ctor(sourcePos, ctor.explicit(), new WithPos<>(namePos, resolution), params, ctor.as()));
      }
      case Pattern.Tuple tuple -> {
        var newCtx = new Ref<>(context);
        var patterns = tuple.patterns().map(p -> subpatterns(newCtx, p));
        yield Tuple.of(
          bindAs(tuple.as(), newCtx.value, tuple.sourcePos()),
          new Pattern.Tuple(tuple.sourcePos(), tuple.explicit(), patterns, tuple.as()));
      }
      case Pattern.Bind bind -> {
        var maybe = findPatternDef(context, bind.sourcePos(), bind.bind().name());
        if (maybe != null) yield Tuple.of(context, new Pattern.Ctor(bind, maybe));
        else yield Tuple.of(context.bind(bind.bind(), bind.sourcePos(), var -> false), bind);
      }
      default -> Tuple.of(context, pattern);
    };
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
}
