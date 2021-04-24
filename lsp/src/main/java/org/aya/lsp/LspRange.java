// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp;

import org.aya.api.error.SourcePos;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;

public class LspRange {
  public static final Range NONE = new Range();
  public static @NotNull Range from(@NotNull SourcePos sourcePos) {
    if (sourcePos == SourcePos.NONE) return NONE;
    return new Range(new Position(sourcePos.startLine() - 1, sourcePos.startColumn()),
      new Position(sourcePos.endLine() - 1, sourcePos.endColumn() + 1));
  }
}
