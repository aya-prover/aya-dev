// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import kala.control.Option;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

/**
 * A container that has scope structure,
 * this is designed to growable, but not shrinkable or modify existing record.
 *
 * @param <This> the <b>final</b> type that implemented this interface
 */
public interface Scoped<K, V, This extends Scoped<K, V, This>> {
  @Contract(pure = true) @Nullable This parent();

  /**
   * @return this object
   * @implSpec {@code self() == this}
   */
  @Contract("-> this") @NotNull This self();

  /**
   * @return new {@link This} object, which {@link #parent()} is <code>this</code> object
   */
  @Contract("-> new") @NotNull This derive();

  /**
   * Fold from bottom, you may treat a {@link Scoped} as a telescope, then
   * this method is exactly {@code foldRight}.
   */
  default <R> R fold(R acc, BiFunction<This, R, R> folder) {
    @Nullable var scope = self();
    while (scope != null) {
      acc = folder.apply(scope, acc);
      scope = scope.parent();
    }

    return acc;
  }

  @ApiStatus.Internal
  @NotNull Option<V> getLocal(@NotNull K key);

  @ApiStatus.Internal
  void putLocal(@NotNull K key, @NotNull V value);

  @ApiStatus.Internal
  default boolean containsLocal(@NotNull K key) {
    return getLocal(key).isDefined();
  }

  default @NotNull V get(@NotNull K key) {
    return fold(Option.<V>none(), (self, acc) -> acc.orElse(() -> self.getLocal(key)))
      .getOrThrow(() -> new Panic(STR."¿: Not in scope: \{key}"));
  }

  default void put(@NotNull K key, @NotNull V value) {
    if (contains(key)) throw new Panic(STR."Existing \{key}");
    putLocal(key, value);
  }

  default boolean contains(@NotNull K key) {
    return fold(false, (self, acc) -> acc || self.containsLocal(key));
  }
}
