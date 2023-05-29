// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import org.aya.tyck.trace.Trace;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * This is the third base-base class of a tycker.
 * It has a localCtx and supports some term mocking functions.
 * <p>
 * TODO: delete this
 *
 * @author ice1000
 * @see #subscoped(Supplier)
 */
public abstract sealed class LetListTycker extends ConcreteAwareTycker permits UnifiedTycker {
  protected LetListTycker(@NotNull Reporter reporter, Trace.@Nullable Builder traceBuilder, @NotNull TyckState state) {
    super(reporter, traceBuilder, state);
  }
}
