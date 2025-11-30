// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.function.IndexedFunction;
import org.aya.generic.TermVisitor;
import org.aya.syntax.core.term.call.ClassCall;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.jetbrains.annotations.NotNull;

/**
 * Term that constructs an instance of some {@link ClassCall}.
 * NewTerm has the same structure as a fully applied {@link ClassCall},
 * as it has only one instance.
 */
public record NewTerm(@NotNull ClassCall inner) implements StableWHNF {
  public NewTerm {
    assert inner.args().size() == inner.ref().members().size();
  }

  public @NotNull NewTerm update(@NotNull ClassCall classCall) {
    if (classCall == inner) return this;
    return new NewTerm(classCall);
  }

  @Override
  public @NotNull Term descent(@NotNull TermVisitor visitor) {
    // not `f.apply(0, inner)`, since the `ClassCall` is considered to be flatten
    return inner.descent(visitor);
  }
}
