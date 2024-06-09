// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.ref;

import org.jetbrains.annotations.NotNull;

public final class MemberVar implements AnyVar {
  public final int index;
  public final @NotNull LocalVar name;
  public MemberVar(int index, @NotNull LocalVar name) {
    this.index = index;
    this.name = name;
  }
  @Override public @NotNull String name() { return name.name(); }
}
