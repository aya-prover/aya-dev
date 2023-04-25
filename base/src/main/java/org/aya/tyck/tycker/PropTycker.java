// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import org.aya.core.term.ErrorTerm;
import org.aya.core.term.SortTerm;
import org.aya.core.term.Term;
import org.aya.util.error.InternalException;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.tyck.ExprTycker;
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
 * @see #inProp(Term)
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

  /**
   * @return false means unsure or not a prop type.
   */
  public boolean inProp(@NotNull Term type) {
    return switch (synthesizer().tryPress(type)) {
      case null -> false;
      case SortTerm sort -> sort.isProp();
      case ErrorTerm err -> true;
      case Term sort -> throw new InternalException("Expected computeType() to produce a sort, got "
        + type.toDoc(AyaPrettierOptions.pretty()).debugRender()
        + " : " + sort.toDoc(AyaPrettierOptions.pretty()).debugRender());
    };
  }
}
