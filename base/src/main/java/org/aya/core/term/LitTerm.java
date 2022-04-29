// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.core.repr.AyaShape;
import org.jetbrains.annotations.NotNull;

public sealed interface LitTerm extends Term {
  record ShapedInt(
    int integer,
    @NotNull AyaShape shape,
    // TODO: remove the type
    @NotNull CallTerm.Data type
  ) implements LitTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitShapedLit(this, p);
    }

    public @NotNull Term constructorForm() {
      return shape.transformTerm(this, type);
    }
  }
}
