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
  @NotNull MutableMap<String, MutableMap<ModuleName, T>> table
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
  public @NotNull MapView<ModuleName, T> resolveUnqualified(@NotNull String unqualifiedName) {
    var names = table.getOrNull(unqualifiedName);
    return names != null ? names.view() : MapView.empty();
  }

  /** @return the mutable version of {@link #resolveUnqualified(String)} */
  public @NotNull MutableMap<ModuleName, T> resolveUnqualifiedMut(@NotNull String unqualifiedName) {
    return table.getOrPut(unqualifiedName, MutableMap::create);
  }

  /**
   * Getting a symbol of an unqualified name {@param unqualifiedName} in component {@param component}
   *
   * @param component       the component
   * @param unqualifiedName the unqualified name
   * @return none if not found
   */
  public @NotNull Option<T> getQualifiedMaybe(@NotNull ModuleName component, @NotNull String unqualifiedName) {
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

    var uniqueCandidates = candidates.valuesView().distinct();
    if (uniqueCandidates.size() != 1) return Result.err(Error.Ambiguous);

    return Result.ok(uniqueCandidates.iterator().next());
  }

  /**
   * Trying to get a symbol of an optional component and an unqualified name.
   *
   * @param component       an optional component, none if `This`
   * @param unqualifiedName the unqualified name
   */
  public @NotNull Result<T, Error> getMaybe(@NotNull ModuleName component, @NotNull String unqualifiedName) {
    return switch (component) {
      case ModuleName.Qualified qualified -> getQualifiedMaybe(component, unqualifiedName).toResult(Error.NotFound);
      case ModuleName.ThisRef aThis -> getUnqualifiedMaybe(unqualifiedName);
    };
  }

  public boolean contains(@NotNull String unqualified) {
    return resolveUnqualified(unqualified).isNotEmpty();
  }

  public boolean contains(@NotNull ModuleName component, @NotNull String unqualified) {
    return resolveUnqualified(unqualified).containsKey(component);
  }

  public enum Error {
    NotFound,
    Ambiguous
  }

  /**
   * Adding a new symbol which can be referred by `{componentName}::{name}`
   *
   * @implNote This method always overwrites the symbol that is added in the past.
   */
  public Option<T> add(
    @NotNull ModuleName componentName,
    @NotNull String name,
    @NotNull T ref
  ) {
    var candidates = resolveUnqualifiedMut(name);
    return candidates.put(componentName, ref);
  }

  public Option<T> remove(@NotNull ModuleName component, @NotNull String unqualifiedName) {
    return table().getOption(unqualifiedName).flatMap(x -> x.remove(component));
  }

  public Result<T, Error> removeDefinitely(@NotNull String unqualifiedName) {
    var candidates = resolveUnqualifiedMut(unqualifiedName);

    if (candidates.isEmpty()) return Result.err(Error.NotFound);

    var uniqueCandidates = candidates.valuesView().distinct().toImmutableSeq();
    if (uniqueCandidates.size() != 1) return Result.err(Error.Ambiguous);

    var result = uniqueCandidates.iterator().next();
    candidates.clear();

    return Result.ok(result);
  }

  public Result<T, Error> removeDefinitely(@NotNull ModuleName component, @NotNull String unqualifiedName) {
    return switch (component) {
      case ModuleName.Qualified qualified -> {
        var result = remove(component, unqualifiedName);

        if (result.isEmpty()) {
          yield Result.err(Error.NotFound);
        } else {
          yield Result.ok(result.get());
        }
      }
      case ModuleName.ThisRef aThis -> removeDefinitely(unqualifiedName);
    };
  }

  public @NotNull SetView<String> keysView() {
    return table().keysView();
  }

  public @NotNull MapView<String, Map<ModuleName, T>> view() {
    return table().view().mapValues((k, v) -> v);
  }

  public void forEach(@NotNull BiConsumer<String, Map<ModuleName, T>> action) {
    table().forEach(action);
  }
}
