// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.morphism.Constants;
import org.aya.compiler.morphism.ir.IrCodeBuilder;
import org.aya.compiler.morphism.ir.IrVariable;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// @param normalizer must set before using
public record SerializerContext(
  @Nullable IrVariable normalizer,
  @NotNull ModuleSerializer.MatchyRecorder recorder
) {
  public @NotNull IrVariable serializeTermUnderTele(
    @NotNull IrCodeBuilder builder,
    @NotNull Term term,
    @NotNull ImmutableSeq<IrVariable> argTerms
  ) {
    return new TermSerializer(builder, this, null, argTerms)
      .serialize(term);
  }

  /**
   * Apply {@link #normalizer} to {@param term}, note that this method may introduce statements (i.e. variable declaration).
   *
   * @return the java expr of wh-normalized term
   */
  public @NotNull IrVariable whnf(@NotNull IrCodeBuilder builder, @NotNull IrVariable term) {
    if (normalizer == null) return term;
    var invoke = builder.invoke(Constants.CLOSURE, normalizer, ImmutableSeq.of(term));
    return builder.checkcast(invoke, Constants.CD_Term);
  }
}
