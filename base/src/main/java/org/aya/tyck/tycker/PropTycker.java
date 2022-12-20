// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import org.aya.concrete.Expr;
import org.aya.core.term.*;
import org.aya.generic.util.InternalException;
import org.aya.generic.util.NormalizeMode;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.TyckState;
import org.aya.tyck.error.SortPiError;
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
 * @see #sortPi(Expr, SortTerm, SortTerm)
 * @see #withResult
 */
public sealed abstract class PropTycker extends UnifiedTycker permits ExprTycker {
  protected PropTycker(@NotNull Reporter reporter, Trace.@Nullable Builder traceBuilder, @NotNull TyckState state) {
    super(reporter, traceBuilder, state);
  }

  public boolean inProp = false;

  private <T> T withInProp(boolean inProp, @NotNull Supplier<T> supplier) {
    var origin = this.inProp;
    this.inProp = inProp;
    try {
      return supplier.get();
    } finally {
      this.inProp = origin;
    }
  }

  public <T> T withResult(@NotNull Term result, @NotNull Supplier<T> supplier) {
    return withInProp(isPropType(result), supplier);
  }

  public boolean isPropType(@NotNull Term type) {
    var sort = type.computeType(state, localCtx).normalize(state, NormalizeMode.WHNF);
    if (sort instanceof MetaTerm meta) {
      var value = state.metas().getOption(meta.ref());
      if (value.isDefined()) return isPropType(value.get());
      state.metaNotProps().add(meta.ref()); // assert not Prop
      return false;
    }
    if (sort instanceof SortTerm s) return s.isProp();
    if (sort instanceof ErrorTerm) return false;
    throw new InternalException("Expected computeType() to produce a sort, got "
      + type.toDoc(AyaPrettierOptions.pretty())
      + " : " + sort.toDoc(AyaPrettierOptions.pretty()));
  }

  public @NotNull SortTerm sortPi(@NotNull Expr expr, @NotNull SortTerm domain, @NotNull SortTerm codomain) {
    return sortPiImpl(new SortPiParam(reporter, expr), domain, codomain);
  }

  public static @NotNull SortTerm sortPi(@NotNull SortTerm domain, @NotNull SortTerm codomain) throws IllegalArgumentException {
    return sortPiImpl(null, domain, codomain);
  }

  private record SortPiParam(@NotNull Reporter reporter, @NotNull Expr expr) {
  }

  private static @NotNull SortTerm sortPiImpl(@Nullable SortPiParam p, @NotNull SortTerm domain, @NotNull SortTerm codomain) throws IllegalArgumentException {
    var result = PiTerm.max(domain, codomain);
    if (p == null) {
      assert result != null;
      return result;
    }
    if (result == null) {
      p.reporter.report(new SortPiError(p.expr.sourcePos(), domain, codomain));
      return SortTerm.Type0;
    } else {
      return result;
    }
  }
}
