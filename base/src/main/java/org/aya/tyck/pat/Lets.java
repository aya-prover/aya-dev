// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.mutable.MutableMap;
import kala.control.Option;
import org.aya.core.term.Term;
import org.aya.ref.AnyVar;
import org.aya.tyck.ExprTycker;
import org.jetbrains.annotations.NotNull;

/**
 * A {@code let expression} sequence, it stores things like {@code let var : type := term}
 * TODO[hoshino]: move to another place
 */
public record Lets(
  @NotNull MutableMap<@NotNull AnyVar, ExprTycker.@NotNull Result> map
) {
  public Lets() {
    this(MutableMap.create());
  }

  /**
   * Add a var-(term : type) record
   */
  public @NotNull Lets addDirectly(@NotNull AnyVar var, @NotNull Term term, @NotNull Term type) {
    map.put(var, new ExprTycker.TermResult(term, type));
    return this;
  }

  public @NotNull Lets addDirectly(@NotNull Lets lets) {
    map.putAll(lets.map());
    return this;
  }

  public @NotNull Option<ExprTycker.Result> getOption(@NotNull AnyVar var) {
    return map.getOption(var);
  }

  public Lets derive() {
    return new Lets(MutableMap.from(map));
  }

  public void clear() {
    map.clear();
  }
}
