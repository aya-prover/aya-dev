// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.util;

import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.NotNull;

/// @param x line, count from 1
/// @param y column, count from 0
public record XY(int x, int y) {
  public boolean inside(@NotNull SourcePos sourcePos) {
    return sourcePos.compareVisually(x, y) == 0;
  }

  /// Check whether the position is after the {@param sourcePos}
  public boolean after(@NotNull SourcePos sourcePos) {
    return sourcePos.compareVisually(x, y) > 0;
  }

  public boolean before(@NotNull SourcePos sourcePos) {
    return sourcePos.compareVisually(x, y) < 0;
  }
}
