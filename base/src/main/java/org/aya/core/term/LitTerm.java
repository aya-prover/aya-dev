// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.repr.CodeShape;
import org.jetbrains.annotations.NotNull;

public sealed interface LitTerm extends Term {
  record ShapedInt(int value, @NotNull ImmutableSeq<CodeShape> possibleShapes) implements LitTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitShapedLit(this, p);
    }
  }
}
