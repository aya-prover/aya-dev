// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import kala.control.Option;
import kala.tuple.Unit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

/// A map-like container that has a scope structure,
/// this is designed to be growable, but not shrinkable or modify existing record.
///
/// @param <This> the **final** (at least, final for user) type that implemented this interface
public interface Scoped<K, V, This extends Scoped<K, V, This>> {
  @Contract(pure = true) @Nullable This parent();

  /// @return this object
  /// @implSpec `self() == this`
  @Contract("-> this") @NotNull This self();

  /// @return new [This] object, which [#parent()] is `this` object
  @Contract("-> new") @NotNull This derive();

  /// Fold from bottom, you may treat a [Scoped] as a telescope, then
  /// this method is exactly `foldRight`.
  default <R> @Nullable R findFirst(Function<This, @Nullable R> folder) {
    @Nullable var scope = self();
    while (scope != null) {
      var acc = folder.apply(scope);
      if (acc != null) return acc;
      scope = scope.parent();
    }

    return null;
  }

  default void forEach(@NotNull Consumer<This> consumer) {
    @Nullable var scope = self();
    while (scope != null) {
      consumer.accept(scope);
      scope = scope.parent();
    }
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
    var found = findFirst(self -> self.getLocal(key).getOrNull());
    assert found != null : "¿: Not in scope: " + key;
    return found;
  }

  default void put(@NotNull K key, @NotNull V value) {
    assert !contains(key) : "Existing " + key;
    putLocal(key, value);
  }

  default boolean contains(@NotNull K key) {
    return findFirst(self -> self.containsLocal(key) ? Unit.INSTANCE : null) != null;
  }
}
