// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public record ConjCof(@NotNull ImmutableSeq<CofTerm> elements) {
  public @NotNull ConjCof add(@NotNull CofTerm c) {
    return new ConjCof(elements.appended(c));
  }
  public @NotNull ConjCof descent(@NotNull IndexedFunction<Term, Term> f) {
    return new ConjCof(elements().map(e -> e.descent(f)));
  }
  public ConjCof map(@NotNull Function<Term, Term> f) {
    return new ConjCof(elements().map(e -> e.map(f)));
  }
  public @NotNull CofTerm head() {
    return elements().get(0);
  }
  public @NotNull ConjCof tail() {
    return new ConjCof(elements().drop(1));
  }
  public boolean empty() {
    return elements().isEmpty();
  }
  public @NotNull ConjCof add(@NotNull ConjCof c) {
    return new ConjCof(elements().appendedAll(c.elements()));
  }
}
