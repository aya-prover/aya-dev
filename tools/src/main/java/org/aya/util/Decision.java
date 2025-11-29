// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;
import java.util.function.Supplier;

// https://github.com/JetBrains/intellij-community/blob/c5faaba98523d9f336cc78e986221bf23c53b7fb/platform/util/multiplatform/src/com/intellij/util/ThreeState.kt
public enum Decision {
  NO, UNSURE, YES;

  public boolean toBoolean() {
    assert this != UNSURE;
    return this == YES;
  }

  /// Identity: [#YES]
  /// Zero: [#NO]
  public static @NotNull Decision min(@NotNull Decision lhs, @NotNull Decision rhs) {
    return Decision.values()[Math.min(lhs.ordinal(), rhs.ordinal())];
  }

  public static <T> @NotNull Decision minOfAll(@NotNull ImmutableSeq<T> seq, @NotNull Function<T, Decision> f) {
    var acc = YES;

    for (var elem : seq) {
      acc = acc.lub(f.apply(elem));
      if (acc == NO) return NO;
    }

    return acc;
  }

  public @NotNull Decision lub(@NotNull Decision other) {
    return min(this, other);
  }
  public @NotNull Decision lub(@NotNull Supplier<Decision> f) {
    if (this == NO) return this;
    return lub(f.get());
  }

  public <T> @NotNull RelDec<T> lubRelDec(@NotNull Supplier<RelDec<T>> f) {
    if (this == NO) return RelDec.no();
    return f.get().lub(this);
  }

  public boolean atLeast(@NotNull Decision other) {
    return other.ordinal() <= this.ordinal();
  }

  public static @NotNull Decision from(boolean state) {
    return state ? YES : NO;
  }

  public <T> @NotNull RelDec<T> toRelDec(@NotNull Supplier<T> onSucc) {
    return switch (this) {
      case NO -> RelDec.no();
      case UNSURE -> RelDec.unsure();
      case YES -> RelDec.yes(onSucc.get());
    };
  }

  public <T> @NotNull RelDec<T> toRelDec(@NotNull T proof) {
    return switch (this) {
      case NO -> RelDec.no();
      case UNSURE -> RelDec.unsure();
      case YES -> RelDec.of(proof);
    };
  }
}
