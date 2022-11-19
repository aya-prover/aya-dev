// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import com.intellij.openapi.util.TextRange;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record HighlightInfo(
  @NotNull TextRange sourcePos,
  @NotNull HighlightInfoType type
) implements Comparable<HighlightInfo> {
  public HighlightInfo(@NotNull SourcePos sourcePos, @NotNull HighlightInfoType type) {
    this(new TextRange(sourcePos.tokenStartIndex(), sourcePos.tokenEndIndex() + 1), type);
  }

  @Override public int compareTo(@NotNull HighlightInfo o) {
    return Integer.compare(
      sourcePos.getStartOffset(),
      o.sourcePos.getStartOffset()
    );
  }
}
