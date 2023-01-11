// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import org.jetbrains.annotations.NotNull;

public enum Decision {
  NO, MAYBE, YES;

  public static @NotNull Decision confident(boolean b) {
    return b ? YES : NO;
  }

  public static @NotNull Decision optimistic(boolean b) {
    return b ? YES : MAYBE;
  }

  public @NotNull Decision max(Decision other) {
    return ordinal() >= other.ordinal() ? this : other;
  }

  public @NotNull Decision min(Decision other) {
    return ordinal() <= other.ordinal() ? this : other;
  }
}
