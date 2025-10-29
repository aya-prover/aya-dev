// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.UnaryOperator;

public sealed interface RelDec<T> {
  sealed interface Strict<T> extends RelDec<T> {
    @Override default RelDec.@NotNull Strict<T> map(@NotNull UnaryOperator<T> f) { return this; }
    RelDec.@NotNull Strict<T> flatMap(@NotNull Function<T, Strict<T>> bind);
    @Override @NotNull RelDec.Strict<T> lub(@NotNull Decision state);
  }

  sealed interface Claim<T> extends RelDec<T> { }

  record Proof<T>(@NotNull T proof) implements Strict<T> {
    @Override public @NotNull Decision downgrade() { return Decision.YES; }
    @Override public @NotNull T getOrNull() { return proof; }
    @Override public @NotNull Strict<T> map(@NotNull UnaryOperator<T> f) { return new Proof<>(f.apply(proof)); }
    @Override public @NotNull Strict<T> lub(@NotNull Decision state) {
      return switch (state) {
        case NO, UNSURE -> new StrictClaim<>(state);
        case YES -> this;
      };
    }
    @Override public @NotNull Strict<T> flatMap(@NotNull Function<T, Strict<T>> bind) {
      return bind.apply(proof);
    }
  }

  record StrictClaim<T>(@NotNull Decision claim) implements Strict<T>, Claim<T> {
    public StrictClaim { assert claim != Decision.YES; }
    @Override public @NotNull Decision downgrade() { return claim; }
    @Override public @Nullable T getOrNull() { return null; }
    @Override public @NotNull Strict<T> lub(@NotNull Decision state) {
      // never fail, claim is never YES, thus claim.lub(state) is never YES
      return new StrictClaim<>(claim.lub(state));
    }
    @Override public @NotNull Strict<T> flatMap(@NotNull Function<T, Strict<T>> bind) { return this; }
  }
  enum YesClaim implements Claim<Object> {
    INSTANCE;
    @Override public @NotNull Decision downgrade() { return Decision.YES; }
    @Override public @NotNull Object getOrNull() { return Panic.unreachable(); }
    @Override public @NotNull RelDec<Object> lub(@NotNull Decision state) {
      if (state == Decision.YES) return this;
      return from(state);
    }
  }
  static <T> Proof<T> of(T proof) { return new Proof<>(proof); }
  static <T> Claim<T> from(@NotNull Decision claim) {
    if (claim == Decision.YES) return yes();
    return new StrictClaim<>(claim);
  }
  static <T> Claim<T> yes() { return (Claim<T>) YesClaim.INSTANCE; }
  static <T> StrictClaim<T> unsure() { return new StrictClaim<>(Decision.UNSURE); }
  static <T> StrictClaim<T> no() { return new StrictClaim<>(Decision.NO); }
  static <T> RelDec.@NotNull Strict<T> ofNullable(@Nullable T maybeProof) {
    if (maybeProof != null) return of(maybeProof);
    return no();
  }

  @NotNull Decision downgrade();
  default boolean isNo() { return downgrade() == Decision.NO; }
  default boolean isYes() { return downgrade() == Decision.YES; }
  @NotNull RelDec<T> lub(@NotNull Decision state);

  /// @return the containing proof, null if this is [Claim] with [Decision#UNSURE] or [Decision#NO].
  /// @throws Panic if this is [Claim] with [Decision#YES]
  @Nullable T getOrNull();

  default @NotNull T get() {
    if (!(this instanceof RelDec.Proof<T>(var proof))) return Panic.unreachable();
    return proof;
  }

  default @NotNull RelDec<T> map(@NotNull UnaryOperator<T> f) {
    return this;
  }
}
