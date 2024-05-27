// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.util;

import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record XY(int x, int y) {
  public boolean inside(@NotNull SourcePos sourcePos) {
    return sourcePos.containsVisually(x, y);
  }
}
