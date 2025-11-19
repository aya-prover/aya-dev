// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public record DisjunctionCof(@NotNull ImmutableSeq<ConjunctionCof> elements) {
  public @NotNull DisjunctionCof add(ConjunctionCof c) {
    return new DisjunctionCof(elements().appended(c));
  }

  public @NotNull DisjunctionCof decent(@NotNull IndexedFunction<Term, Term> f) {
    return new DisjunctionCof(elements().map(e -> e.descent(f)));
  }

  public DisjunctionCof map(@NotNull Function<Term, Term> f) {
    return new DisjunctionCof(elements().map(e -> e.map(f)));
  }

  public boolean empty() {
    return elements().isEmpty();
  }

  public ConjunctionCof head() {
    return elements().get(0);
  }

  public DisjunctionCof tail() {
    return new DisjunctionCof(elements().drop(1));
  }
}
