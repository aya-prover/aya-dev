// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.utils;

import org.aya.util.error.SourcePos;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;

/** @see Position */
public record XY(int x, int y) {
  public XY(@NotNull Position position) {
    this(position.getLine() + 1, position.getCharacter() - 1);
  }

  public boolean inside(@NotNull SourcePos sourcePos) {
    return sourcePos.contains(x, y);
  }
}
