// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.utils;

import org.aya.util.error.SourcePos;
import org.javacs.lsp.Range;
import org.jetbrains.annotations.NotNull;

/** @see Range */
public record XYXY(@NotNull XY start, @NotNull XY end) {
  public XYXY(@NotNull Range position) {
    this(new XY(position.start), new XY(position.end));
  }

  public boolean contains(@NotNull SourcePos sourcePos) {
    return sourcePos.startLine() >= start.x() && sourcePos.endLine() <= end.x();
  }
}
