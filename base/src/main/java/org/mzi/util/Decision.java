// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.util;

import org.jetbrains.annotations.NotNull;

public enum Decision {
  NO, MAYBE, YES;

  public @NotNull Decision max(Decision other) {
    return ordinal() >= other.ordinal() ? this : other;
  }

  public @NotNull Decision min(Decision other) {
    return ordinal() <= other.ordinal() ? this : other;
  }
}
