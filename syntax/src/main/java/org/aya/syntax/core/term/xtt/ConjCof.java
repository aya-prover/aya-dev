// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableArrayList;
import kala.function.IndexedFunction;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

public record ConjCof(@NotNull ImmutableSeq<EqCof> elements) {
  public @NotNull ConjCof add(@NotNull EqCof c) {
    return new ConjCof(elements.appended(c));
  }
  public @NotNull ConjCof descent(@NotNull IndexedFunction<Term, Term> f) {
    if (elements().isEmpty()) return this;
    var ret = MutableArrayList.from(elements());
    for (int i = 0; i < ret.size(); i++) {
      ret.set(i, ret.get(i).descent(f));
    }
    return new ConjCof(ret.toImmutableArray());
  }
  public @NotNull EqCof head() {
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
