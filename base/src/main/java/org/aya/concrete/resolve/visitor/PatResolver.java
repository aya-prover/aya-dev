// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.visitor;

import org.aya.api.error.SourcePos;
import org.aya.concrete.Pattern;
import org.aya.concrete.resolve.context.Context;
import org.aya.generic.Atom;
import org.aya.ref.LocalVar;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.tuple.Tuple2;
import org.glavo.kala.value.Ref;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public final class PatResolver implements
  Pattern.Clause.Visitor<Context, Pattern.Clause>,
  Pattern.Visitor<Context, Tuple2<Context, Pattern>>,
  Atom.Visitor<Pattern, Context, Tuple2<Context, Atom<Pattern>>> {
  public static final @NotNull PatResolver INSTANCE = new PatResolver();

  private PatResolver() {
  }

  @Override public Pattern.Clause visitMatch(Pattern.Clause.@NotNull Match match, Context context) {
    var ctx = new Ref<>(context);
    var pats = match.patterns().stream().sequential().map(pat -> {
      var res = pat.accept(this, ctx.value);
      ctx.value = res._1;
      return res._2;
    }).collect(Buffer.factory());
    return new Pattern.Clause.Match(pats, match.expr().resolve(ctx.value));
  }

  @Override public Pattern.Clause visitAbsurd(Pattern.Clause.@NotNull Absurd absurd, Context context) {
    return absurd;
  }

  @Contract(value = "_, _ -> fail", pure = true)
  @Override public Tuple2<Context, Pattern> visitAtomic(Pattern.@NotNull Atomic atomic, Context context) {
    var atom = atomic.atom().accept(this, context);
    var sourcePos = atomic.sourcePos();
    var newCtx = bindAs(atomic.as(), atom._1, sourcePos);
    return Tuple.of(newCtx, new Pattern.Atomic(sourcePos, atom._2, atomic.as()));
  }

  private Context bindAs(LocalVar as, Context ctx, SourcePos sourcePos) {
    return as != null ?  ctx.bind(as, sourcePos) : ctx;
  }

  @Contract(value = "_, _ -> fail", pure = true)
  @Override public Tuple2<Context, Pattern> visitCtor(Pattern.@NotNull Ctor ctor, Context context) {
    var newCtx = new Ref<>(context);
    var params = ctor.params().map(p -> {
      var pats = p.accept(this, newCtx.value);
      newCtx.value = pats._1;
      return pats._2;
    });
    return new Tuple2<>(newCtx.value, new Pattern.Ctor(ctor.sourcePos(), ctor.name(), params, ctor.as()));
  }
}
