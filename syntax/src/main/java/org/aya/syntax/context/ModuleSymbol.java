// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.context;

import kala.collection.MapView;
import kala.collection.SetView;
import kala.collection.mutable.MutableMap;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

/**
 * A container of symbols.
 */
public record ModuleSymbol<T>(@NotNull MutableMap<String, Candidate<T>> table) {
  public ModuleSymbol() { this(MutableMap.create()); }

  public ModuleSymbol(@NotNull ModuleSymbol<T> other) {
    this(MutableMap.from(other.table));
  }

  // FIXME: dont put, we need a read-only get
  public @NotNull Candidate<T> get(@NotNull String name) {
    return table.getOrPut(name, Candidate.Imported::empty);
  }

  public @NotNull Candidate<T> getOrPut(@NotNull String name) {
    return table.getOrPut(name, Candidate.Imported::empty);
  }

  public boolean contains(@NotNull String name) { return !get(name).isEmpty(); }

  /**
   * @param name   name for symbol
   * @param symbol the symbol
   */
  public void add(@NotNull String name, T symbol, ModuleName fromModule) {
    var candy = Candidate.of(fromModule, symbol);
    var old = get(name);
    table.put(name, old.merge(candy));
  }

  public @NotNull SetView<String> keysView() { return table.keysView(); }
  public @NotNull MapView<String, Candidate<T>> view() { return table.view(); }

  public void forEach(@NotNull BiConsumer<String, Candidate<T>> action) {
    table.forEach((name, candy) -> {
      if (!candy.isEmpty()) action.accept(name, candy);
    });
  }
}
