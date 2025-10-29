// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.UnaryOperator;

public sealed interface RelDec<T> {
  sealed interface Strict<T> extends RelDec<T> {
    @Override
    default RelDec.@NotNull Strict<T> map(@NotNull UnaryOperator<T> f) {
      return this;
    }

    RelDec.@NotNull Strict<T> flatMap(@NotNull Function<T, Strict<T>> bind);

    @Override
    @NotNull
    RelDec.Strict<T> lub(@NotNull ThreeState state);
  }

  sealed interface Claim<T> extends RelDec<T> { }

  record Proof<T>(@NotNull T proof) implements Strict<T> {
    @Override
    public @NotNull ThreeState downgrade() {
      return ThreeState.YES;
    }

    @Override
    public @NotNull T getOrNull() {
      return proof;
    }

    @Override
    public @NotNull Strict<T> map(@NotNull UnaryOperator<T> f) {
      return new Proof<>(f.apply(proof));
    }

    @Override
    public @NotNull Strict<T> lub(@NotNull ThreeState state) {
      return switch (state) {
        case NO, UNSURE -> new StrictClaim<>(state);
        case YES -> this;
      };
    }

    @Override
    public @NotNull Strict<T> flatMap(@NotNull Function<T, Strict<T>> bind) {
      return bind.apply(proof);
    }
  }

  record StrictClaim<T>(@NotNull ThreeState claim) implements Strict<T>, Claim<T> {
    public StrictClaim {
      assert claim != ThreeState.YES;
    }

    @Override
    public @NotNull ThreeState downgrade() {
      return claim;
    }

    @Override
    public @Nullable T getOrNull() {
      return null;
    }

    @Override
    public @NotNull Strict<T> lub(@NotNull ThreeState state) {
      // never fail, claim is never YES, thus claim.lub(state) is never YES
      return new StrictClaim<>(claim.lub(state));
    }

    @Override
    public @NotNull Strict<T> flatMap(@NotNull Function<T, Strict<T>> bind) {
      return this;
    }
  }

  enum YesClaim implements Claim<Object> {
    INSTANCE;

    @Override
    public @NotNull ThreeState downgrade() {
      return ThreeState.YES;
    }

    @Override
    public @NotNull Object getOrNull() {
      return Panic.unreachable();
    }

    @Override
    public @NotNull RelDec<Object> lub(@NotNull ThreeState state) {
      if (state == ThreeState.YES) return this;
      return from(state);
    }
  }

  static <T> Proof<T> of(T proof) {
    return new Proof<>(proof);
  }

  static <T> Claim<T> from(@NotNull ThreeState claim) {
    if (claim == ThreeState.YES) return yes();
    return new StrictClaim<>(claim);
  }

  static <T> Claim<T> yes() {
    return (Claim<T>) YesClaim.INSTANCE;
  }

  static <T> StrictClaim<T> unsure() {
    return new StrictClaim<>(ThreeState.UNSURE);
  }

  static <T> StrictClaim<T> no() {
    return new StrictClaim<>(ThreeState.NO);
  }

  static <T> @NotNull RelDec.Strict<T> ofNullable(@Nullable T maybeProof) {
    if (maybeProof != null) return of(maybeProof);
    return no();
  }

  @NotNull ThreeState downgrade();

  default boolean isNo() {
    return downgrade() == ThreeState.NO;
  }

  default boolean isYes() {
    return downgrade() == ThreeState.YES;
  }

  @NotNull RelDec<T> lub(@NotNull ThreeState state);

  /// @return the containing proof, null if this is [Claim] with [ThreeState#UNSURE] or [ThreeState#NO].
  /// @throws Panic if this is [Claim] with [ThreeState#YES]
  @Nullable T getOrNull();

  default @NotNull T get() {
    if (!(this instanceof RelDec.Proof<T>(var proof))) return Panic.unreachable();
    return proof;
  }

  default @NotNull RelDec<T> map(@NotNull UnaryOperator<T> f) {
    return this;
  }
}
