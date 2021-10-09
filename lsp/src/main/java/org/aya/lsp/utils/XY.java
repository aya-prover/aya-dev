// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.utils;

import org.aya.api.error.SourcePos;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;

public record XY(int x, int y) {
  public XY(@NotNull Position position) {
    this(position.getLine() + 1, position.getCharacter());
  }

  public boolean inside(@NotNull SourcePos sourcePos) {
    return sourcePos.contains(x, y);
  }
}
