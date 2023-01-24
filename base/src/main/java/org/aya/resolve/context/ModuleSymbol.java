// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.Map;
import kala.collection.MapView;
import kala.collection.SetView;
import kala.control.Option;
import kala.control.Result;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

public interface ModuleSymbol<T> {

  /**
   * Getting the candidates of an unqualified name
   *
   * @param unqualifiedName the unqualified name
   * @return the candidates, probably empty
   */
  @NotNull Map<ModulePath, T> resolveUnqualified(@NotNull String unqualifiedName);

  /**
   * Getting a symbol of an unqualified name {@param unqualifiedName} in component {@param component}
   *
   * @param component       the component
   * @param unqualifiedName the unqualified name
   * @return none if not found
   */
  default @NotNull Option<T> getQualifiedMaybe(@NotNull ModulePath component, @NotNull String unqualifiedName) {
    return resolveUnqualified(unqualifiedName).getOption(component);
  }

  /**
   * Trying to get a symbol of an unqualified name definitely.
   *
   * @param unqualifiedName the unqualified name
   */
  default @NotNull Result<T, Error> getUnqualifiedMaybe(@NotNull String unqualifiedName) {
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
  default @NotNull Result<T, Error> getMaybe(@NotNull ModulePath component, @NotNull String unqualifiedName) {
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

  default boolean contains(@NotNull String unqualified) {
    return resolveUnqualified(unqualified).isNotEmpty();
  }

  default boolean containsDefinitely(@NotNull String unqualified) {
    return resolveUnqualified(unqualified).size() == 1;
  }

  default boolean containsDefinitely(@NotNull ModulePath component, @NotNull String unqualified) {
    return resolveUnqualified(unqualified).containsKey(component);
  }

  /// region API Adapter

  @NotNull Map<String, Map<ModulePath, T>> toMap();

  @NotNull SetView<String> keysView();

  @NotNull MapView<String, Map<ModulePath, T>> view();

  void forEach(@NotNull BiConsumer<String, Map<ModulePath, T>> action);

  /// endregion

  enum Error {
    NotFound,
    Ambiguous
  }
}
