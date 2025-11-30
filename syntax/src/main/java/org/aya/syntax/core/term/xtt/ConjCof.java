// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.TermVisitor;
import org.jetbrains.annotations.NotNull;

public record ConjCof(@NotNull ImmutableSeq<EqCof> elements) {
  public @NotNull ConjCof add(@NotNull EqCof c) {
    return new ConjCof(elements.appended(c));
  }
  public @NotNull ConjCof descent(@NotNull TermVisitor visitor) {
    if (elements().isEmpty()) return this;
    // TODO: check if mapped [elements] are identical to [elements]
    return new ConjCof(elements().map(cof -> cof.descent(visitor)));
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
