// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.sort;

import org.aya.api.ref.LevelVar;
import org.jetbrains.annotations.NotNull;

public sealed interface Level {
  final class Unsolved implements Level {
    public static final @NotNull Unsolved INSTANCE = new Unsolved();

    private Unsolved() {
    }
  }

  record Constant(int value) {
  }

  record Reference(@NotNull LevelVar<Level> ref, int lift) {
  }
}
