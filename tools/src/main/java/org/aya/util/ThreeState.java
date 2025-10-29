// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

// https://github.com/JetBrains/intellij-community/blob/c5faaba98523d9f336cc78e986221bf23c53b7fb/platform/util/multiplatform/src/com/intellij/util/ThreeState.kt
public enum ThreeState {
  NO, UNSURE, YES;

  public boolean toBoolean() {
    assert this != UNSURE;
    return this == YES;
  }

  /// Identity: [#YES]
  /// Zero: [#NO]
  public static @NotNull ThreeState min(@NotNull ThreeState lhs, @NotNull ThreeState rhs) {
    return ThreeState.values()[Math.min(lhs.ordinal(), rhs.ordinal())];
  }

  public @NotNull ThreeState lub(@NotNull ThreeState other) {
    return min(this, other);
  }
  public @NotNull ThreeState lub(@NotNull Supplier<ThreeState> f) {
    if (this == NO) return this;
    return lub(f.get());
  }

  public <T> RelDec.@NotNull Strict<T> lubRelDec(@NotNull Supplier<RelDec.Strict<T>> f) {
    if (this == NO) return RelDec.no();
    return f.get().lub(this);
  }

  public boolean atLeast(@NotNull ThreeState other) {
    return other.ordinal() <= this.ordinal();
  }

  public static @NotNull ThreeState from(boolean state) {
    return state ? YES : NO;
  }

  public <T> RelDec.@NotNull Claim<T> toClaim() {
    return RelDec.from(this);
  }

  public <T> RelDec.@NotNull Strict<T> toRelDec(@NotNull Supplier<T> onSucc) {
    return switch (this) {
      case NO -> RelDec.no();
      case UNSURE -> RelDec.unsure();
      case YES -> RelDec.of(onSucc.get());
    };
  }

  public <T> RelDec.@NotNull Strict<T> toRelDec(@NotNull T proof) {
    return switch (this) {
      case NO -> RelDec.no();
      case UNSURE -> RelDec.unsure();
      case YES -> RelDec.of(proof);
    };
  }
}
