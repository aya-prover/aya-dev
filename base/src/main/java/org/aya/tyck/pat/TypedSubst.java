// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.mutable.MutableMap;
import kala.control.Option;
import org.aya.core.term.Term;
import org.aya.core.visitor.Subst;
import org.aya.ref.AnyVar;
import org.aya.tyck.Result;
import org.jetbrains.annotations.NotNull;

public record TypedSubst(
  @NotNull Subst subst,
  @NotNull MutableMap<@NotNull AnyVar, @NotNull Term> type
) {
  public TypedSubst() {
    this(new Subst(), MutableMap.create());
  }

  public @NotNull TypedSubst addDirectly(@NotNull AnyVar var, @NotNull Term term, @NotNull Term type) {
    this.subst.addDirectly(var, term);
    this.type.put(var, type);

    return this;
  }

  public @NotNull TypedSubst addDirectly(@NotNull TypedSubst subst) {
    this.subst.addAllDirectly(subst.subst);
    this.type.putAll(subst.type);

    return this;
  }

  public @NotNull Option<Result> getOption(@NotNull AnyVar var) {
    return subst.map().getOption(var).flatMap(term ->
      this.type.getOption(var).map(type ->
        new Result.Default(term, type))
    );
  }

  public @NotNull TypedSubst derive() {
    return new TypedSubst(
      subst.derive(),
      MutableMap.from(type)
    );
  }

  public void clear() {
    subst.clear();
    type.clear();
  }
}
