// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.Constants;
import org.aya.compiler.free.FreeCodeBuilder;
import org.aya.compiler.free.FreeExprBuilder;
import org.aya.compiler.free.FreeJavaExpr;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record SerializerContext(
  @Nullable FreeJavaExpr normalizer,
  @NotNull ModuleSerializer.MatchyRecorder recorder
) {
  public @NotNull FreeJavaExpr serializeTermUnderTele(
    @NotNull FreeExprBuilder builder,
    @NotNull Term term,
    @NotNull ImmutableSeq<FreeJavaExpr> argTerms
  ) {
    return new TermExprializer(builder, this, argTerms)
      .serialize(term);
  }

  public @NotNull FreeJavaExpr serializeTermUnderTele(
    @NotNull FreeExprBuilder builder, @NotNull Term term,
    @NotNull FreeJavaExpr argsTerm, int size
  ) {
    return serializeTermUnderTele(builder, term, AbstractExprializer.fromSeq(builder, Constants.CD_Term, argsTerm, size));
  }

  /**
   * Apply {@link #normalizer} to {@param term}, note that this method may introduce statements (i.e. variable declaration).
   *
   * @return the java expr of whnfed term
   */
  public @NotNull FreeJavaExpr whnf(@NotNull FreeCodeBuilder builder, @NotNull FreeJavaExpr term) {
    if (normalizer == null) return term;
    var whnfed = builder.checkcast(builder.invoke(Constants.CLOSURE, normalizer, ImmutableSeq.of(term)), Constants.CD_Term);
    var var = builder.makeVar(Constants.CD_Term, whnfed);
    return builder.refVar(var);
  }
}
