// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.Map;
import kala.collection.MapView;
import kala.collection.SetView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import kala.control.Result;
import kala.tuple.Tuple2;
import kala.value.LazyValue;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

/**
 * A container of symbols.
 */
public record ModuleSymbol2<T>(@NotNull MutableMap<String, Candidate<T>> table) {
  public ModuleSymbol2() { this(MutableMap.create()); }

  public ModuleSymbol2(@NotNull ModuleSymbol2<T> other) {
    this(MutableMap.from(other.table));
  }

  /** @apiNote should not use this after {@link #asMut} is called. */
  public record UnqualifiedResolve<T>(
    @NotNull MapView<ModuleName, T> map,
    @NotNull LazyValue<MutableMap<ModuleName, T>> asMut
  ) {
    public @NotNull ImmutableSeq<T> uniqueCandidates() {
      return map.valuesView().distinct().toImmutableSeq();
    }

    public @NotNull ImmutableSeq<ModuleName> moduleNames() {
      return map.keysView().toImmutableSeq();
    }
  }

  public @NotNull Candidate<T> get(@NotNull String name) {
    return table.getOrDefault(name, Candidate.Imported.empty());
  }

  /**
   * @param name   name for symbol
   * @param symbol the symbol
   */
  public void add(@NotNull String name, T symbol, ModuleName fromModule) {
    var candy = switch (fromModule) {
      case ModuleName.Qualified qualified -> Candidate.Imported.of(qualified, symbol);
      case ModuleName.ThisRef thisRef -> new Candidate.Defined<>(symbol);
    };

    var old = get(name);
    table.put(name, old.merge(candy));
  }

  /**
   * Trying to get a symbol of name {@param name}.
   */
  public @NotNull Result<T, Error> getMaybe(@NotNull String name) {
    var candidates = get(name);

    if (candidates.isEmpty()) return Result.err(Error.NotFound);
    if (candidates.isAmbiguous()) return Result.err(Error.Ambiguous);

    return Result.ok(candidates.get());
  }

  public boolean contains(@NotNull String name) {
    return !get(name).isEmpty();
  }

  public enum Error {
    NotFound,
    Ambiguous
  }

  public @NotNull SetView<String> keysView() {
    return table.keysView();
  }

  public @NotNull MapView<String, Candidate<T>> view() {
    return table.view();
  }

  public void forEach(@NotNull BiConsumer<String, Candidate<T>> action) {
    table.forEach(action);
  }
}
