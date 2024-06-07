// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractSerializer<T> implements SourceBuilder {
  public record JitParam(@NotNull String name, @NotNull String type) { }

  protected final @NotNull SourceBuilder sourceBuilder;

  protected AbstractSerializer(@NotNull SourceBuilder builder) {
    assert builder != this : "Dont do this";
    this.sourceBuilder = builder;
  }

  @Override public @NotNull StringBuilder builder() { return sourceBuilder.builder(); }
  @Override public @NotNull NameGenerator nameGen() { return sourceBuilder.nameGen(); }
  @Override public int indent() {
    return sourceBuilder.indent();
  }

  @Override public void runInside(@NotNull Runnable runnable) {
    sourceBuilder.runInside(runnable);
  }
  /**
   * the implementation should keep {@link SourceBuilder#indent} after invocation.
   */
  public abstract AbstractSerializer<T> serialize(T unit);

  public String result() { return builder().toString(); }

  protected @NotNull String serializeTermUnderTele(@NotNull Term term, @NotNull String argsTerm, int size) {
    return serializeTermUnderTele(term, SourceBuilder.fromSeq(argsTerm, size));
  }

  protected @NotNull String serializeTermUnderTele(@NotNull Term term, @NotNull ImmutableSeq<String> argTerms) {
    return new TermExprializer(sourceBuilder.nameGen(), argTerms)
      .serialize(term);
  }
}
