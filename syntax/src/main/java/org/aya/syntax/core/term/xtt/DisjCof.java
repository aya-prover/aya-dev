// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableArrayList;
import kala.function.IndexedFunction;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public record DisjCof(@NotNull ImmutableSeq<ConjCof> elements) {
  public @NotNull DisjCof add(ConjCof c) {
    return new DisjCof(elements().appended(c));
  }

  public @NotNull DisjCof descent(@NotNull IndexedFunction<Term, Term> f) {
    if (elements().isEmpty()) return this;
    var ret = MutableArrayList.from(elements());
    for (int i = 0; i < ret.size(); i++) {
      ret.set(i, ret.get(i).descent(f));
    }
    return new DisjCof(ret.toImmutableArray());
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
