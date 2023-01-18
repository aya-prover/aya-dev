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
import java.util.function.BiFunction;

public interface ModuleSymbol<T extends ContextUnit> {

  /**
   * Getting the candidates of an unqualified name
   *
   * @param unqualifiedName the unqualified name
   * @return the candidates, probably empty
   */
  @NotNull Map<ModulePath, T> getCandidates(@NotNull String unqualifiedName);

  /**
   * Getting a symbol of an unqualified name {@param unqualifiedName} in component {@param component}
   *
   * @param component       the component
   * @param unqualifiedName the unqualified name
   * @return none if not found
   */
  @NotNull Option<T> getQualifiedMaybe(@NotNull ModulePath component, @NotNull String unqualifiedName);

  /**
   * Trying to get a symbol of an unqualified name definitely.
   *
   * @param unqualifiedName the unqualified name
   */
  @NotNull Result<T, Error> getUnqualifiedDefinitely(@NotNull String unqualifiedName);

  /**
   * Trying to get a symbol of an optional component and an unqualified name.
   *
   * @param component       an optional component, none if `This`
   * @param unqualifiedName the unqualified name
   */
  @NotNull Result<T, Error> getDefinitely(@NotNull ModulePath component, @NotNull String unqualifiedName);

  boolean contains(@NotNull String unqualified);

  boolean containsDefinitely(@NotNull String unqualified);

  boolean containsDefinitely(@NotNull ModulePath component, @NotNull String unqualified);

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
