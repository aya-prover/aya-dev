// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.TermVisitor;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.jetbrains.annotations.NotNull;

public record DisjCofNF(@NotNull ImmutableSeq<ConjCofNF> elements) implements StableWHNF {
  public @NotNull DisjCofNF add(ConjCofNF c) {
    return new DisjCofNF(elements().appended(c));
  }

  @Override public @NotNull DisjCofNF descent(@NotNull TermVisitor visitor) {
    if (elements().isEmpty()) return this;
    // TODO: see ConjCof
    return new DisjCofNF(elements.map(t -> t.descent(visitor)));
  }

  public boolean empty() {
    return elements().isEmpty();
  }

  public ConjCofNF head() {
    return elements().get(0);
  }

  public DisjCofNF tail() {
    return new DisjCofNF(elements().drop(1));
  }
}
