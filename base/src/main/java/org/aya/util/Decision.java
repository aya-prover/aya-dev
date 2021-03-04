// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.util;

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
