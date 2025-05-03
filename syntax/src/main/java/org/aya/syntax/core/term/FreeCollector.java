// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableSet;
import org.aya.syntax.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public record FreeCollector(@NotNull MutableSet<FreeTermLike> frees) implements UnaryOperator<Term> {
  public FreeCollector() { this(MutableSet.create()); }
  @Override public Term apply(Term term) {
    if (term instanceof FreeTermLike free) frees.add(free);
    else term.descent(this);
    return term;
  }
  public @NotNull ImmutableSeq<FreeTermLike> collected() { return frees.toSeq(); }
}
