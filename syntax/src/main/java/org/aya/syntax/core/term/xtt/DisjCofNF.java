// Copyright (c) 2020-2026 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.TermVisitor;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.jetbrains.annotations.NotNull;

public record DisjCofNF(@NotNull ImmutableSeq<ConjCofNF> elements) implements StableWHNF {
  @Override public @NotNull DisjCofNF descent(@NotNull TermVisitor visitor) {
    if (elements().isEmpty()) return this;
    var mapped = elements.map(t -> t.descent(visitor));
    if (mapped.sameElements(elements())) return this;
    return new DisjCofNF(mapped);
  }
}
