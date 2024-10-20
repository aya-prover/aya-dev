// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
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
    @Override
    public @NotNull Candidate<T> merge(@NotNull Candidate<T> symbol) {
      assert !(symbol instanceof Candidate.Defined<T>);
      return this;
    }

    @Override
    public boolean isAmbiguous() {
      return false;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public T get() {
      return symbol;
    }

    @Override
    public @NotNull ImmutableSeq<ModuleName> from() {
      return ImmutableSeq.of(ModuleName.This);
    }
  }

  /**
   * Default candidate, it represents a candidate list that is imported from other module
   *
   * @param symbols a list of candidates with some module names says where they come from.
   *                it is a list of module name cause one symbol can comes from different module.
   *                Also, the intersection of any two module name sets should be empty.
   * @param <T>
   */
  record Imported<T>(@NotNull ImmutableMap<T, ImmutableSeq<ModuleName.Qualified>> symbols) implements Candidate<T> {
    public static <T> @NotNull Candidate<T> empty() {
      return new Imported<>(ImmutableMap.empty());
    }

    public static <T> @NotNull Candidate<T> of(@NotNull ModuleName.Qualified from, @NotNull T symbol) {
      return new Imported<>(ImmutableMap.of(symbol, ImmutableSeq.of(from)));
    }

    @Override
    public boolean isAmbiguous() {
      return symbols.size() > 1;
    }

    @Override
    public boolean isEmpty() {
      return symbols.isEmpty();
    }

    @Override
    public T get() {
      return symbols.keysView().stream().findFirst().get();
    }

    @Override
    public @NotNull ImmutableSeq<ModuleName> from() {
      return ImmutableSeq.from(symbols.valuesView().flatMap(x -> x));
    }

    @Override
    public @NotNull Candidate<T> merge(@NotNull Candidate<T> candy) {
      return switch (candy) {
        case Candidate.Defined<T> v -> v;
        case Candidate.Imported<T> imported -> new Imported<>(null);    // TODO: merge
      };
    }
  }
}
