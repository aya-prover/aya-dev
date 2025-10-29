// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface RelDec<T> {
  record Proof<T>(@NotNull T proof) implements RelDec<T> {
    @Override public @NotNull Decision downgrade() { return Decision.YES; }
    @Override public @NotNull T getOrNull() { return proof; }
    @Override public @NotNull RelDec<T> lub(@NotNull Decision state) {
      return switch (state) {
        case NO, UNSURE -> new Claim<>(state);
        case YES -> this;
      };
    }
  }

  record Claim<T>(@NotNull Decision claim) implements RelDec<T> {
    @Override public @NotNull Decision downgrade() { return claim; }
    @Override public @Nullable T getOrNull() { return null; }
    @Override public @NotNull RelDec<T> lub(@NotNull Decision state) {
      return new Claim<>(claim.lub(state));
    }
  }
  static <T> Proof<T> of(T proof) { return new Proof<>(proof); }
  static <T> Claim<T> from(@NotNull Decision claim) {
    return switch (claim) {
      case NO -> no();
      case UNSURE -> unsure();
      case YES -> yes();
    };
  }
  @NotNull Claim<Object> YES_INSTANCE = new Claim<>(Decision.YES);
  @NotNull Claim<Object> UNSURE_INSTANCE = new Claim<>(Decision.UNSURE);
  @NotNull Claim<Object> NO_INSTANCE = new Claim<>(Decision.NO);
  @SuppressWarnings("unchecked") static <T> Claim<T> yes() { return (Claim<T>) YES_INSTANCE; }
  @SuppressWarnings("unchecked") static <T> Claim<T> unsure() { return (Claim<T>) UNSURE_INSTANCE; }
  @SuppressWarnings("unchecked") static <T> Claim<T> no() { return (Claim<T>) NO_INSTANCE; }

  static @NotNull <T> RelDec<T> yes(@Nullable T maybeProof) {
    if (maybeProof != null) return of(maybeProof);
    return yes();
  }

  @NotNull Decision downgrade();
  default boolean isNo() { return downgrade() == Decision.NO; }
  default boolean isYes() { return downgrade() == Decision.YES; }
  @NotNull RelDec<T> lub(@NotNull Decision state);

  /// @return the containing proof, null if this is [Claim] with [Decision#UNSURE] or [Decision#NO].
  /// @throws Panic if this is [Claim] with [Decision#YES]
  @Nullable T getOrNull();

  default @NotNull T get() {
    if (!(this instanceof Proof<T>(var proof))) return Panic.unreachable();
    return proof;
  }
}
