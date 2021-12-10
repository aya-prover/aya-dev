// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.env;

import kala.collection.SeqView;
import kala.collection.mutable.DynamicSeq;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableMap;
import org.aya.api.ref.LocalVar;
import org.aya.core.term.Term;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * @author re-xyr, ice1000
 */
@Debug.Renderer(hasChildren = "true", childrenArray = "extract().toArray()")
public record MapLocalCtx(@NotNull MutableMap<LocalVar, Term> localMap, @Nullable LocalCtx parent) implements LocalCtx {
  public MapLocalCtx() {
    this(MutableLinkedHashMap.of(), null);
  }

  @Override public void remove(@NotNull SeqView<LocalVar> vars) {
    vars.forEach(localMap::remove);
  }

  @Override public @Nullable Term getLocal(@NotNull LocalVar var) {
    return localMap.getOrNull(var);
  }

  @Override public void put(@NotNull LocalVar var, @NotNull Term term) {
    localMap.set(var, term);
  }

  @Override public boolean isEmpty() {
    return localMap.isEmpty() && (parent == null || parent.isEmpty());
  }

  @Override public <T> T with(@NotNull LocalVar var, @NotNull Term type, @NotNull Supplier<T> action) {
    localMap.put(var, type);
    try {
      return action.get();
    } finally {
      localMap.remove(var);
    }
  }

  @Override public void extractToLocal(@NotNull DynamicSeq<Term.Param> dest) {
    localMap.mapTo(dest, (k, v) -> new Term.Param(k, v, false));
  }
}
