// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import org.jetbrains.annotations.NotNull;

public enum SortKind {
  Type, Set, Prop, ISet;

  public boolean hasLevel() {
    return this == Type || this == Set;
  }

  public @NotNull SortKind max(@NotNull SortKind other) {
    if (this == Set || other == Set) return Set;
    if (this == Type || other == Type) return Type;
    // Prop or ISet
    return this == other ? this : Type;
  }
}
