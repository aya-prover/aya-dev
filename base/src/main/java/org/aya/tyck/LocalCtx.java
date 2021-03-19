// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import org.aya.core.term.Term;
import org.aya.ref.LocalVar;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * @author re-xyr
 */
public record LocalCtx(@NotNull MutableMap<LocalVar, Term> localMap) implements Cloneable {
  public LocalCtx() {
    this(MutableHashMap.of());
  }

  public <T> T with(@NotNull LocalVar var, @NotNull Term type, @NotNull Supplier<T> action) {
    localMap.put(var, type);
    var result = action.get();
    localMap.remove(var);
    return result;
  }

  public @NotNull ImmutableSeq<Term.Param> extract() {
    var ctx = Buffer.<Term.Param>of();
    localMap.mapTo(ctx, (k, v) -> new Term.Param(k, v, false));
    return ctx.toImmutableSeq();
  }

  public @NotNull Term get(LocalVar var) {
    return localMap.get(var);
  }

  public void put(LocalVar var, @NotNull Term term) {
    localMap.set(var, term);
  }

  public @NotNull LocalCtx clone() {
    return new LocalCtx(MutableHashMap.from(localMap));
  }
}
