// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public record DisjCof(@NotNull ImmutableSeq<ConjCof> elements) {
  public @NotNull DisjCof add(ConjCof c) {
    return new DisjCof(elements().appended(c));
  }

  public @NotNull DisjCof decent(@NotNull IndexedFunction<Term, Term> f) {
    return new DisjCof(elements().map(e -> e.descent(f)));
  }

  // Usually map(this::whnf).
  public DisjCof map(@NotNull Function<Term, Term> f) {
    return new DisjCof(elements().map(e -> e.map(f)));
  }

  public boolean empty() {
    return elements().isEmpty();
  }

  public ConjCof head() {
    return elements().get(0);
  }

  public DisjCof tail() {
    return new DisjCof(elements().drop(1));
  }
}
