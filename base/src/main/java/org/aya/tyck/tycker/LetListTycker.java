// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import org.aya.tyck.pat.TypedSubst;
import org.aya.tyck.trace.Trace;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * This is the third base-base class of a tycker.
 * It has a localCtx and supports some term mocking functions.
 *
 * @author ice1000
 * @see #subscoped(Supplier)
 */
public abstract sealed class LetListTycker extends ConcreteAwareTycker permits UnifiedTycker {
  public @NotNull TypedSubst definitionEqualities = new TypedSubst();

  protected LetListTycker(@NotNull Reporter reporter, Trace.@Nullable Builder traceBuilder, @NotNull TyckState state) {
    super(reporter, traceBuilder, state);
  }

  public <R> R subscoped(@NotNull Supplier<R> action) {
    var parentCtx = this.ctx;
    var parentSubst = this.definitionEqualities;

    this.ctx = parentCtx.deriveMap();
    this.definitionEqualities = parentSubst.derive();

    var result = action.get();

    this.definitionEqualities = parentSubst;
    this.ctx = parentCtx;

    return result;
  }
}
