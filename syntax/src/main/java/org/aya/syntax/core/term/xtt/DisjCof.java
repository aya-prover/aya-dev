// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.TermVisitor;
import org.jetbrains.annotations.NotNull;

public record DisjCof(@NotNull ImmutableSeq<ConjCof> elements) {
  public @NotNull DisjCof add(ConjCof c) {
    return new DisjCof(elements().appended(c));
  }

  public @NotNull DisjCof descent(@NotNull TermVisitor visitor) {
    if (elements().isEmpty()) return this;
    // TODO: see ConjCof
    return new DisjCof(elements.map(t -> t.descent(visitor)));
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
