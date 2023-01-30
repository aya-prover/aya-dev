// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.Map;
import kala.collection.MapView;
import kala.collection.SetView;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import kala.control.Result;
import kala.tuple.Tuple2;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

/**
 * A container of symbols.
 *
 * @param table `Unqualified -> (Component -> Symbol)`<br/>
 *              It says a `Symbol` can be referred by `{Component}::{Unqualified}`
 * @apiNote the methods that end with `Definitely` will get/remove only one symbol or fail if ambiguous.
 */
public record ModuleSymbol<T>(
  @NotNull MutableMap<String, MutableMap<ModulePath, T>> table
) {
  public ModuleSymbol() {
    this(MutableMap.create());
  }

  public ModuleSymbol(@NotNull ModuleSymbol<T> other) {
    this(other.table.toImmutableSeq().collect(MutableMap.collector(
      Tuple2::component1,
      x -> MutableMap.from(x.component2()))
    ));
  }

  /**
   * Getting the candidates of an unqualified name
   *
   * @param unqualifiedName the unqualified name
   * @return the candidates, probably empty
   */
  public @NotNull Map<ModulePath, T> resolveUnqualified(@NotNull String unqualifiedName) {
    var result = table().getOption(unqualifiedName);
    if (result.isEmpty()) {
      return Map.empty();
    } else {
      return result.get();
    }
  }

  /**
   * Getting a symbol of an unqualified name {@param unqualifiedName} in component {@param component}
   *
   * @param component       the component
   * @param unqualifiedName the unqualified name
   * @return none if not found
   */
  public @NotNull Option<T> getQualifiedMaybe(@NotNull ModulePath component, @NotNull String unqualifiedName) {
    return resolveUnqualified(unqualifiedName).getOption(component);
  }

  /**
   * Trying to get a symbol of an unqualified name definitely.
   *
   * @param unqualifiedName the unqualified name
   */
  public @NotNull Result<T, Error> getUnqualifiedMaybe(@NotNull String unqualifiedName) {
    var candidates = resolveUnqualified(unqualifiedName);

    if (candidates.isEmpty()) return Result.err(Error.NotFound);
    if (candidates.size() != 1) return Result.err(Error.Ambiguous);

    return Result.ok(candidates.iterator().next().getValue());
  }

  /**
   * Trying to get a symbol of an optional component and an unqualified name.
   *
   * @param component       an optional component, none if `This`
   * @param unqualifiedName the unqualified name
   */
  public @NotNull Result<T, Error> getMaybe(@NotNull ModulePath component, @NotNull String unqualifiedName) {
    return switch (component) {
      case ModulePath.Qualified qualified -> {
        var result = getQualifiedMaybe(component, unqualifiedName);
        if (result.isEmpty()) {
          yield Result.err(Error.NotFound);
        }

        yield Result.ok(result.get());
      }
      case ModulePath.This aThis -> getUnqualifiedMaybe(unqualifiedName);
    };
  }

  public boolean contains(@NotNull String unqualified) {
    return resolveUnqualified(unqualified).isNotEmpty();
  }

  public boolean containsDefinitely(@NotNull String unqualified) {
    return resolveUnqualified(unqualified).size() == 1;
  }

  public boolean containsDefinitely(@NotNull ModulePath component, @NotNull String unqualified) {
    return resolveUnqualified(unqualified).containsKey(component);
  }

  public enum Error {
    NotFound,
    Ambiguous
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

  public @NotNull MutableMap<ModulePath, T> getMut(@NotNull String unqualifiedName) {
    return table().getOrPut(unqualifiedName, MutableMap::create);
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

  public @NotNull SetView<String> keysView() {
    return table().keysView();
  }

  public @NotNull MapView<String, Map<ModulePath, T>> view() {
    return table().view().mapValues((k, v) -> v);
  }

  public void forEach(@NotNull BiConsumer<String, Map<ModulePath, T>> action) {
    table().forEach(action);
  }
}
