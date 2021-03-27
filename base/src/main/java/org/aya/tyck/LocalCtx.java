// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import org.aya.api.ref.LocalVar;
import org.aya.core.term.Term;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * @author re-xyr, ice1000
 */
@Debug.Renderer(hasChildren = "true", childrenArray = "extract().toArray()")
public record LocalCtx(@NotNull MutableMap<LocalVar, Term> localMap, @Nullable LocalCtx parent) {
  public LocalCtx() {
    this(MutableHashMap.of(), null);
  }

  public <T> T with(@NotNull LocalVar var, @NotNull Term type, @NotNull Supplier<T> action) {
    localMap.put(var, type);
    var result = action.get();
    localMap.remove(var);
    return result;
  }

  public @NotNull ImmutableSeq<Term.Param> extract() {
    var ctx = Buffer.<Term.Param>of();
    var map = this;
    while (map != null) {
      map.localMap.mapTo(ctx, (k, v) -> new Term.Param(k, v, false));
      map = map.parent;
    }
    return ctx.toImmutableSeq();
  }

  @Contract(pure = true) public @NotNull Term get(LocalVar var) {
    var result = localMap.getOrElse(var, () -> parentGet(var));
    assert result != null;
    return result;
  }

  @Contract(pure = true) private @Nullable Term parentGet(LocalVar var) {
    return parent != null ? parent.get(var) : null;
  }

  public void put(@NotNull LocalVar var, @NotNull Term term) {
    localMap.set(var, term);
  }

  public boolean isEmpty() {
    return localMap.isEmpty() && (parent == null || parent.isEmpty());
  }

  @Contract(" -> new") public @NotNull LocalCtx derive() {
    return new LocalCtx(MutableMap.create(), this);
  }
}
