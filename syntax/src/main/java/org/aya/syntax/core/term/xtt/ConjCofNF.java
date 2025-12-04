// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
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
    // TODO: check if mapped [elements] are identical to [elements]
    return new ConjCofNF(elements().map(cof -> cof.descent(visitor)));
  }
  public @NotNull EqCofTerm head() {
    return elements().get(0);
  }
  public @NotNull ConjCofNF tail() {
    return new ConjCofNF(elements().drop(1));
  }
  public boolean empty() {
    return elements().isEmpty();
  }
  public @NotNull ConjCofNF add(@NotNull ConjCofNF c) {
    return new ConjCofNF(elements().appendedAll(c.elements()));
  }
}
