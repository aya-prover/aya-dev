// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic.stmt;

import org.jetbrains.annotations.NotNull;

public sealed interface TyckOrder {
  @NotNull TyckUnit unit();

  /** "Need to check and obtain the type signature of a definition" */
  record Head(@NotNull TyckUnit unit) implements TyckOrder {
    @Override public boolean equals(Object obj) {
      return obj instanceof Head head && head.unit() == unit();
    }

    public Body toBody() { return new Body(unit); }
  }

  /** Need to check the full implementation of a definition */
  record Body(@NotNull TyckUnit unit) implements TyckOrder {
    @Override public boolean equals(Object obj) {
      return obj instanceof Body body && body.unit() == unit();
    }
  }
}
