// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.jetbrains.annotations.NotNull;

public sealed interface PrimTerm extends Term {
  final class End implements PrimTerm {
    private final boolean isRight;

    public static End LEFT = new End(false);
    public static End RIGHT = new End(true);

    private End(boolean isRight) {
      this.isRight = isRight;
    }

    public boolean isRight() {
      return isRight;
    }
  }

  record Str(@NotNull String string) implements PrimTerm {

  }
}
