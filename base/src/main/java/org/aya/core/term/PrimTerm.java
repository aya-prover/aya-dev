// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.jetbrains.annotations.NotNull;

public sealed interface PrimTerm extends Term {
  boolean LEFT = false;
  boolean RIGHT = true;

  record End(boolean isRight) implements PrimTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitEnd(this, p);
    }

    public boolean left() {
      return isRight() == LEFT;
    }

    public boolean right() {
      return isRight() == RIGHT;
    }
  }
}
