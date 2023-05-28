// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.mutable.MutableMap;
import kala.control.Option;
import org.aya.core.term.Term;
import org.aya.core.visitor.Subst;
import org.aya.ref.LocalVar;
import org.aya.tyck.Result;
import org.jetbrains.annotations.NotNull;

public record DefEq(
  @NotNull Subst subst,
  @NotNull MutableMap<@NotNull LocalVar, @NotNull Term> type
) {
  public DefEq() {
    this(new Subst(), MutableMap.create());
  }

  public @NotNull DefEq addDirectly(@NotNull LocalVar var, @NotNull Term term, @NotNull Term type) {
    this.subst.addDirectly(var, term);
    this.type.put(var, type);

    return this;
  }

  public @NotNull DefEq addDirectly(@NotNull DefEq subst) {
    this.subst.addAllDirectly(subst.subst);
    this.type.putAll(subst.type);

    return this;
  }

  public @NotNull Option<Result> getOption(@NotNull LocalVar var) {
    return subst.map().getOption(var).flatMap(term ->
      this.type.getOption(var).map(type ->
        new Result.Default(term, type))
    );
  }

  public @NotNull DefEq derive() {
    return new DefEq(
      subst.derive(),
      MutableMap.from(type)
    );
  }
}
