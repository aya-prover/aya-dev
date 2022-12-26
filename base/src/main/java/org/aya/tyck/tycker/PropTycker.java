// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import org.aya.core.term.*;
import org.aya.generic.util.InternalException;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.Synthesizer;
import org.aya.tyck.trace.Trace;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * This is the fifth base-base class of a tycker.
 * It has a member isProp, and supports some Prop-related functions.
 *
 * @author tsao-chi
 * @see #isPropType(Term)
 * @see #sortPi(SortTerm, SortTerm)
 * @see #withInProp(boolean, Supplier)
 */
public sealed abstract class PropTycker extends UnifiedTycker permits ExprTycker {
  protected PropTycker(@NotNull Reporter reporter, Trace.@Nullable Builder traceBuilder, @NotNull TyckState state) {
    super(reporter, traceBuilder, state);
  }

  public boolean inProp = false;

  protected final <T> T withInProp(boolean inProp, @NotNull Supplier<T> supplier) {
    var origin = this.inProp;
    this.inProp = inProp;
    try {
      return supplier.get();
    } finally {
      this.inProp = origin;
    }
  }

  public boolean isPropType(@NotNull Term type) {
    var sort = new Synthesizer(state, localCtx).synthesize(type);
    if (sort == null) throw new UnsupportedOperationException("Zaoqi");
    if (sort instanceof MetaTerm meta) {
      state.notInPropMetas().add(meta.ref()); // assert not Prop
      return false;
    }
    if (sort instanceof SortTerm s) return s.isProp();
    if (sort instanceof ErrorTerm) return false;
    throw new InternalException("Expected computeType() to produce a sort, got "
      + type.toDoc(AyaPrettierOptions.pretty())
      + " : " + sort.toDoc(AyaPrettierOptions.pretty()));
  }

  public static @NotNull SortTerm sortPi(@NotNull SortTerm domain, @NotNull SortTerm codomain) throws IllegalArgumentException {
    var result = PiTerm.max(domain, codomain);
    assert result != null;
    return result;
  }
}
