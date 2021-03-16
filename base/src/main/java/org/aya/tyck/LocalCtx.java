// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import org.aya.api.ref.Var;
import org.aya.core.term.Term;
import org.aya.ref.LocalVar;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.ArrayBuffer;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * @author re-xyr
 */
public final class LocalCtx {
  public LocalCtx(@NotNull MutableMap<LocalVar, Term> localMap) {
    this.localMap = localMap;
  }

  public LocalCtx() {}

  public @NotNull MutableMap<LocalVar, Term> localMap = MutableHashMap.of();

  public <T> T with(@NotNull LocalVar var, @NotNull Term type, @NotNull Supplier<T> action) {
    localMap.put(var, type);
    var result = action.get();
    localMap.remove(var);
    return result;
  }

  public @NotNull ImmutableSeq<Term.Param> extract() {
    var ctx = ArrayBuffer.<Term.Param>of();
    localMap.forEach((k, v) -> ctx.append(new Term.Param(k, v, false)));
    return ctx.toImmutableSeq();
  }

  public Term get(LocalVar var) {
    return localMap.get(var);
  }
}
