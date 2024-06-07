// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractExprializer<T> {
  protected final @NotNull NameGenerator nameGen;

  protected AbstractExprializer(@NotNull NameGenerator nameGen) { this.nameGen = nameGen; }

  @SafeVarargs protected final @NotNull String makeAppNew(@NotNull String className, T... terms) {
    return ImmutableSeq.from(terms).joinToString(ExprializeUtils.SEP,
      STR."new \{className}(", ").make()", this::doSerialize);
  }

  protected @NotNull String serializeToImmutableSeq(@NotNull String typeName, @NotNull ImmutableSeq<T> terms) {
    return ExprializeUtils.makeImmutableSeq(typeName, terms.map(this::doSerialize));
  }

  protected abstract @NotNull String doSerialize(@NotNull T term);

  public abstract @NotNull String serialize(T unit);
}
