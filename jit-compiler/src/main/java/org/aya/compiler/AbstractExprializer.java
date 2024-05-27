// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.NameGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public abstract class AbstractExprializer<T> implements AyaSerializer<T> {
  private String lastResult = null;
  protected final @NotNull NameGenerator nameGen;
  public static final String SEP = ", ";

  protected AbstractExprializer(@NotNull NameGenerator nameGen) { this.nameGen = nameGen; }

  protected @NotNull String makeNew(@NotNull String className, String... terms) {
    return ImmutableSeq.from(terms).joinToString(SEP, STR."new \{className}(", ")");
  }

  protected @NotNull String makeNew(@NotNull String className, T... terms) {
    return ImmutableSeq.from(terms).map(this::doSerialize).joinToString(SEP, STR."new \{className}(", ")");
  }

  protected @NotNull String serializeToImmutableSeq(@NotNull String typeName, @NotNull ImmutableSeq<T> terms) {
    return makeImmutableSeq(typeName, terms.map(this::doSerialize));
  }

  protected @NotNull String makeImmutableSeq(@NotNull String typeName, @NotNull ImmutableSeq<String> terms) {
    if (terms.isEmpty()) {
      return STR."\{CLASS_IMMSEQ}.empty()";
    } else {
      return terms.joinToString(SEP, STR."\{CLASS_IMMSEQ}.<\{typeName}>of(", ")");
    }
  }

  protected @NotNull String makeThunk(@NotNull String value) {
    return STR."() -> \{value}";
  }

  protected abstract @NotNull String doSerialize(@NotNull T term);

  @Override public AyaSerializer<T> serialize(T unit) {
    lastResult = doSerialize(unit);
    return this;
  }

  @Override public String result() {
    return Objects.requireNonNull(lastResult);
  }
}
