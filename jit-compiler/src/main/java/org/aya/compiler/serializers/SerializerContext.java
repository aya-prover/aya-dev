// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.morphism.CodeBuilder;
import org.aya.compiler.morphism.Constants;
import org.aya.compiler.morphism.ExprBuilder;
import org.aya.compiler.morphism.JavaExpr;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.FnCall;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record SerializerContext(
  @Nullable JavaExpr normalizer,
  @NotNull ModuleSerializer.MatchyRecorder recorder
) {
  public @NotNull JavaExpr serializeTermUnderTele(
    @NotNull ExprBuilder builder,
    @NotNull Term term,
    @NotNull ImmutableSeq<JavaExpr> argTerms
  ) {
    return new TermExprializer(builder, this, argTerms)
      .serialize(term);
  }
  public @NotNull FnCall serializeTailCallUnderTele(
    @NotNull ExprBuilder builder,
    @NotNull FnCall term,
    @NotNull ImmutableSeq<JavaExpr> argTerms
  ) {
    return new TermExprializer(builder, this, argTerms).serializeTailCall(term);
  }

  public @NotNull JavaExpr serializeTermUnderTele(
    @NotNull ExprBuilder builder, @NotNull Term term,
    @NotNull JavaExpr argsTerm, int size
  ) {
    return serializeTermUnderTele(builder, term, AbstractExprializer.fromSeq(builder, Constants.CD_Term, argsTerm, size));
  }

  /**
   * Apply {@link #normalizer} to {@param term}, note that this method may introduce statements (i.e. variable declaration).
   *
   * @return the java expr of whnfed term
   */
  public @NotNull JavaExpr whnf(@NotNull CodeBuilder builder, @NotNull JavaExpr term) {
    if (normalizer == null) return term;
    var whnfed = builder.checkcast(builder.invoke(Constants.CLOSURE, normalizer, ImmutableSeq.of(term)), Constants.CD_Term);
    var var = builder.makeVar(Constants.CD_Term, whnfed);
    return builder.refVar(var);
  }
}
