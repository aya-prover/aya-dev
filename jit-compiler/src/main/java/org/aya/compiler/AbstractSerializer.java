// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.Constants;
import org.aya.compiler.free.FreeCodeBuilder;
import org.aya.compiler.free.FreeJavaExpr;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractSerializer<T> {
  public record JitParam(@NotNull String name, @NotNull String type) { }

  protected AbstractSerializer() { }

  /**
   * the implementation should keep {@link SourceBuilder#indent} after invocation.
   */
  public abstract AbstractSerializer<T> serialize(@NotNull FreeCodeBuilder builder, T unit);

  protected @NotNull FreeJavaExpr serializeTermUnderTele(@NotNull Term term, @NotNull String argsTerm, int size) {
    return serializeTermUnderTele(term, SourceBuilder.fromSeq(argsTerm, size));
  }

  protected @NotNull FreeJavaExpr serializeTermUnderTele(@NotNull Term term, @NotNull ImmutableSeq<String> argTerms) {
    return new TermExprializer(sourceBuilder.nameGen(), argTerms)
      .serialize(term);
  }

  protected @NotNull FreeJavaExpr serializeTerm(@NotNull Term term) {
    return serializeTermUnderTele(term, ImmutableSeq.empty());
  }

  protected final void buildPanic(@NotNull FreeCodeBuilder builder) {
    builder.exec(builder.exprBuilder().invoke(Constants.PANIC, ImmutableSeq.empty()));
  }
}
