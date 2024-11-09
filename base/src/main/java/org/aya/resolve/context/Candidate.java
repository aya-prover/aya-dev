// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.jetbrains.annotations.NotNull;

/**
 * Candidate represents a list of candidate of symbol resolving
 */
public sealed interface Candidate<T> {
  @NotNull Candidate<T> merge(@NotNull Candidate<T> candy);
  boolean isAmbiguous();
  boolean isEmpty();
  @NotNull ImmutableSeq<ModuleName> from();
  boolean contains(@NotNull ModuleName modName);

  static <T> @NotNull Candidate<T> of(@NotNull ModuleName fromModule, @NotNull T symbol) {
    return switch (fromModule) {
      case ModuleName.Qualified qualified -> Imported.of(qualified, symbol);
      case ModuleName.ThisRef _ -> new Defined<>(symbol);
    };
  }

  /**
   * Returns the only symbol in this candidate, should check {@link #isEmpty()} and {@link #isAmbiguous()} first.
   *
   * @return the only symbol in this candidate, exception if this candidate {@link #isEmpty()} or {@link #isAmbiguous()}
   */
  T get();

  /**
   * A candidate list that only store one symbol, furthermore, it implies the symbol is defined in this module.
   */
  record Defined<T>(T symbol) implements Candidate<T> {
    @Override public @NotNull Candidate<T> merge(@NotNull Candidate<T> symbol) {
      return symbol instanceof Candidate.Defined<T> defined ? defined : this;
    }

    @Override public boolean isAmbiguous() { return false; }
    @Override public boolean isEmpty() { return false; }
    @Override public T get() { return symbol; }
    @Override public @NotNull ImmutableSeq<ModuleName> from() { return ImmutableSeq.of(ModuleName.This); }
    @Override public boolean contains(@NotNull ModuleName modName) { return modName == ModuleName.This; }
  }

  /**
   * Default candidate, it represents a candidate list that is imported from other module
   *
   * @param symbols key: the module that the symbol comes from<br/>
   *                value: the symbol
   * @param <T>
   */
  record Imported<T>(@NotNull ImmutableMap<ModuleName.Qualified, T> symbols) implements Candidate<T> {
    public static <T> @NotNull Candidate<T> empty() {
      return new Imported<>(ImmutableMap.empty());
    }

    public static <T> @NotNull Candidate<T> of(@NotNull ModuleName.Qualified from, @NotNull T symbol) {
      return new Imported<>(ImmutableMap.of(from, symbol));
    }

    @Override public boolean isAmbiguous() {
      return symbols.valuesView().distinct().sizeGreaterThan(1);
    }

    @Override public boolean isEmpty() { return symbols.isEmpty(); }

    @Override public T get() {
      //noinspection OptionalGetWithoutIsPresent
      return symbols.valuesView().stream().findFirst().get();
    }

    @Override
    public @NotNull ImmutableSeq<ModuleName> from() {
      return ImmutableSeq.from(symbols.keysView());
    }

    @Override public boolean contains(@NotNull ModuleName modName) {
      return modName instanceof ModuleName.Qualified qmod && symbols.containsKey(qmod);
    }

    /**
     * Note that we always overwrite symbols comes from the same module, they should be checked before merge (if one wants to report errors)
     */
    @Override public @NotNull Candidate<T> merge(@NotNull Candidate<T> candy) {
      return switch (candy) {
        case Candidate.Defined<T> v -> v;
        case Candidate.Imported<T> imported -> {
          var symbols = MutableMap.<ModuleName.Qualified, T>create();
          symbols.putAll(this.symbols);
          symbols.putAll(imported.symbols);
          yield new Imported<>(ImmutableMap.from(symbols));
        }
      };
    }
  }
}
