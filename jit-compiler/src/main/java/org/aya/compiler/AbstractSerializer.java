// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.NameGenerator;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractSerializer<T> implements SourceBuilder {
  public record JitParam(@NotNull String name, @NotNull String type) { }

  protected final @NotNull SourceBuilder builder;

  protected AbstractSerializer(@NotNull SourceBuilder builder) {
    assert builder != this : "Dont do this";
    this.builder = builder;
  }

  @Override
  public @NotNull StringBuilder builder() {
    return builder.builder();
  }

  @Override
  public @NotNull NameGenerator nameGen() {
    return builder.nameGen();
  }

  @Override
  public int indent() {
    return builder.indent();
  }

  @Override
  public void runInside(@NotNull Runnable runnable) {
    builder.runInside(runnable);
  }
  /**
   * the implementation should keep {@link SourceBuilder#indent} after invocation.
   */
  public abstract AbstractSerializer<T> serialize(T unit);

  public String result() { return builder.toString(); }

  protected @NotNull String serializeTermUnderTele(@NotNull Term term, @NotNull String argsTerm, int size) {
    return serializeTermUnderTele(term, SourceBuilder.fromSeq(argsTerm, size));
  }

  protected @NotNull String serializeTermUnderTele(@NotNull Term term, @NotNull ImmutableSeq<String> argTerms) {
    return new TermExprializer(builder.nameGen(), argTerms)
      .serialize(term);
  }
}
