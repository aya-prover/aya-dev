// Copyright (c) 2020-2026 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.TermVisitor;
import org.jetbrains.annotations.NotNull;

public record ConjCofNF(@NotNull ImmutableSeq<EqCofTerm> elements) {
  public @NotNull ConjCofNF add(@NotNull EqCofTerm c) {
    return new ConjCofNF(elements.appended(c));
  }
  public @NotNull ConjCofNF descent(@NotNull TermVisitor visitor) {
    if (elements().isEmpty()) return this;
    var mapped = elements().map(cof -> cof.descent(visitor));
    if (mapped.sameElements(elements())) return this;
    return new ConjCofNF(mapped);
  }
  public boolean empty() {
    return elements().isEmpty();
  }
}
