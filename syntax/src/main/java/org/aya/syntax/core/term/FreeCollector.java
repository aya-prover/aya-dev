// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableSet;
import org.aya.syntax.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public record FreeCollector(@NotNull MutableSet<LocalVar> frees) implements UnaryOperator<Term> {
  public FreeCollector() { this(MutableSet.create()); }
  @Override public Term apply(Term term) {
    if (term instanceof FreeTerm(var localVar)) frees.add(localVar);
    else term.descent(this);
    return term;
  }
  public @NotNull ImmutableSeq<LocalVar> collected() { return frees.toSeq(); }
}
