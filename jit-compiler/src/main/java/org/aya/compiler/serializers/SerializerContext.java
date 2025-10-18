// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.morphism.Constants;
import org.aya.compiler.morphism.ast.AstCodeBuilder;
import org.aya.compiler.morphism.ast.AstVariable;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.FnCall;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// @param normalizer must set before using
public record SerializerContext(
  @Nullable AstVariable normalizer,
  @NotNull ModuleSerializer.MatchyRecorder recorder
) {
  public @NotNull AstVariable serializeTermUnderTele(
    @NotNull AstCodeBuilder builder,
    @NotNull Term term,
    @NotNull ImmutableSeq<AstVariable> argTerms
  ) {
    return new TermSerializer(builder, this, argTerms)
      .serialize(term);
  }

  public @NotNull ImmutableSeq<AstVariable> serializeTailCallUnderTele(
    @NotNull AstCodeBuilder builder,
    @NotNull FnCall term,
    @NotNull ImmutableSeq<AstVariable> argTerms
  ) {
    return new TermSerializer(builder, this, argTerms).serializeTailCall(term);
  }

  public @NotNull AstVariable serializeTermUnderTele(
    @NotNull AstCodeBuilder builder, @NotNull Term term,
    @NotNull AstVariable argsTerm, int size
  ) {
    return serializeTermUnderTele(builder, term, AbstractExprSerializer.fromSeq(builder, Constants.CD_Term, argsTerm, size));
  }

  /**
   * Apply {@link #normalizer} to {@param term}, note that this method may introduce statements (i.e. variable declaration).
   *
   * @return the java expr of whnfed term
   */
  public @NotNull AstVariable whnf(@NotNull AstCodeBuilder builder, @NotNull AstVariable term) {
    if (normalizer == null) return term;
    var invoke = builder.invoke(Constants.CLOSURE, normalizer, ImmutableSeq.of(term));
    return builder.checkcast(invoke, Constants.CD_Term);
  }
}
