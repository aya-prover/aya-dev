// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.util;

import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record XYXY(@NotNull XY start, @NotNull XY end) {
  public boolean contains(@NotNull SourcePos sourcePos) {
    return sourcePos.startLine() >= start.x() && sourcePos.endLine() <= end.x();
  }
}
