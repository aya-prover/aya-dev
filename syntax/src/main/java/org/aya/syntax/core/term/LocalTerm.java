// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.syntax.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

public record LocalTerm(int index) implements Term {
  public LocalTerm {
    assert index >= 0 : "Sanity check";
  }

  @Override
  public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return this;
  }

  @Override public @NotNull Term bindAt(@NotNull LocalVar var, int depth) {
    return this;
  }

  @Override public @NotNull Term replaceAllFrom(int from, @NotNull ImmutableSeq<Term> list) {
    var i = index - from;
    // * i < 0: this LocalTerm is free
    // * i < list.size(): this LocalTerm is Local, and should be replaced with [list.get(i)]
    // * i >= list.size(): this LocalTerm is Local, but stay
    if (0 <= i && i < list.size()) return list.get(i);
    return this;
  }
}
