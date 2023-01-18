// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.Map;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import kala.control.Result;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A container of symbols.
 *
 * @param table `Unqualified -> (Component -> Symbol)`<br/>
 *              It says a `Symbol` can be referred by `{Component}::{Unqualified}`
 * @apiNote the methods that end with `Definitely` will get/remove only one symbol or fail if ambiguous.
 */
public record MutableModuleSymbol<T extends ContextUnit>(
  @NotNull MutableMap<String, MutableMap<ModulePath, T>> table
) implements ModuleSymbol<T> {
  public MutableModuleSymbol() {
    this(MutableMap.create());
  }

  public MutableModuleSymbol(@NotNull ModuleSymbol<T> other) {
    this(other.toMap().toImmutableSeq()
      .collect(MutableMap.collector(
        Tuple2::component1,
        x -> MutableMap.from(x.component2()))
      )
    );
  }

  @Override
  public @NotNull Map<String, Map<ModulePath, T>> toMap() {
    return Map.from(table().toImmutableSeq());
  }

  /**
   * Adding a new symbol which can be referred by `{componentName}::{name}`
   */
  public Option<T> add(
    @NotNull ModulePath componentName,
    @NotNull String name,
    @NotNull T ref
  ) {
    var candidates = table().getOrPut(name, MutableMap::create);
    return candidates.putIfAbsent(componentName, ref);
  }

  public void addAnyway(
    @NotNull ModulePath componentName,
    @NotNull String name,
    @NotNull T ref
  ) {
    this.table().getOrPut(name, MutableMap::create).put(componentName, ref);
  }

  @Override
  public @NotNull Map<ModulePath, T> getCandidates(@NotNull String unqualifiedName) {
    var result = table().getOption(unqualifiedName);
    if (result.isEmpty()) {
      return Map.empty();
    } else {
      return result.get();
    }
  }

  public @NotNull MutableMap<ModulePath, T> getMut(@NotNull String unqualifiedName) {
    return table().getOrPut(unqualifiedName, MutableMap::create);
  }

  @Override
  public @NotNull Option<T> getQualifiedMaybe(@NotNull ModulePath component, @NotNull String unqualifiedName) {
    return getCandidates(unqualifiedName).getOption(component);
  }

  /**
   * Find a symbol that can be referred by {unqualifiedMame}, and report problems if failed.
   */
  @Override
  public @NotNull Result<T, Error> getUnqualifiedDefinitely(@NotNull String unqualifiedName) {
    var candidates = getCandidates(unqualifiedName);

    if (candidates.isEmpty()) return Result.err(Error.NotFound);
    if (candidates.size() != 1) return Result.err(Error.Ambiguous);

    return Result.ok(candidates.iterator().next().getValue());
  }

  /**
   * Find a symbol that can be referred by {@code {component}::{unqualifiedName}}.
   * For unqualified {@code This::{unqualifiedName}}, this function is the same as {@link MutableModuleSymbol#getUnqualifiedDefinitely(String)}
   */
  @Override
  public @NotNull Result<T, Error> getDefinitely(@NotNull ModulePath component, @NotNull String unqualifiedName) {
    return switch (component) {
      case ModulePath.Qualified qualified -> {
        var result = getQualifiedMaybe(component, unqualifiedName);
        if (result.isEmpty()) {
          yield Result.err(Error.NotFound);
        }

        yield Result.ok(result.get());
      }
      case ModulePath.This aThis -> getUnqualifiedDefinitely(unqualifiedName);
    };
  }

  public Option<T> remove(@NotNull ModulePath component, @NotNull String unqualifiedName) {
    return table().getOption(unqualifiedName).flatMap(x -> x.remove(component));
  }

  public Option<Map<ModulePath, T>> removeAll(@NotNull String unqualifiedName) {
    return Option.narrow(table().remove(unqualifiedName));
  }

  public Result<T, Error> removeDefinitely(@NotNull String unqualifiedName) {
    var candidates = getMut(unqualifiedName);

    if (candidates.isEmpty()) return Result.err(Error.NotFound);
    if (candidates.size() != 1) return Result.err(Error.Ambiguous);

    var result = candidates.iterator().next().getValue();
    candidates.clear();

    return Result.ok(result);
  }

  public Result<T, Error> removeDefinitely(@NotNull ModulePath component, @NotNull String unqualifiedName) {
    return switch (component) {
      case ModulePath.Qualified qualified -> {
        var result = remove(component, unqualifiedName);

        if (result.isEmpty()) {
          yield Result.err(Error.NotFound);
        } else {
          yield Result.ok(result.get());
        }
      }
      case ModulePath.This aThis -> removeDefinitely(unqualifiedName);
    };
  }

  @Override
  public boolean contains(@NotNull String unqualified) {
    return table().containsKey(unqualified);
  }

  @Override
  public boolean containsDefinitely(@NotNull String unqualified) {
    return table()
      .getOption(unqualified)
      .map(x -> x.size() == 1)
      .getOrDefault(false);
  }

  @Override
  public boolean containsDefinitely(@NotNull ModulePath component, @NotNull String unqualified) {
    return table()
      .getOption(unqualified)
      .map(x -> x.containsKey(component))
      .getOrDefault(false);
  }

  @Override
  public void forEach(@NotNull BiConsumer<String, Map<ModulePath, T>> action) {
    table().forEach(action);
  }
}
