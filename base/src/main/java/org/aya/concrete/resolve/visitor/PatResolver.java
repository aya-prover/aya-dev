// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.visitor;

import org.aya.concrete.Pattern;
import org.aya.concrete.resolve.context.Context;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.tuple.Tuple2;
import org.glavo.kala.value.Ref;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public final class PatResolver implements
  Pattern.Clause.Visitor<Context, Pattern.Clause>,
  Pattern.Visitor<Context, Tuple2<Context, Pattern>> {
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
    return null;
  }

  @Override public Tuple2<Context, Pattern> visitAtomic(Pattern.@NotNull Atomic atomic, Context context) {
    throw new UnsupportedOperationException();
  }

  @Override public Tuple2<Context, Pattern> visitCtor(Pattern.@NotNull Ctor ctor, Context context) {
    throw new UnsupportedOperationException();
  }

  @Override public Tuple2<Context, Pattern> visitUnresolved(Pattern.@NotNull Unresolved unr, Context context) {
    throw new UnsupportedOperationException();
  }
}
