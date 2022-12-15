// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.error;

import org.jetbrains.annotations.NotNull;

public interface Span {
  @NotNull String input();

  @NotNull Span.Data normalize(PrettyErrorConfig config);

  enum NowLoc {
    Shot, Start, End, Between, None,
  }

  record Data(
    int startLine,
    int startCol,
    int endLine,
    int endCol
  ) {
    public boolean oneLinear() {
      return startLine == endLine;
    }

    public NowLoc nowLoc(int currentLine) {
      if (currentLine == startLine) return oneLinear() ? NowLoc.Shot : NowLoc.Start;
      if (currentLine == endLine) return NowLoc.End;
      if (currentLine > startLine && currentLine < endLine) return NowLoc.Between;
      return NowLoc.None;
    }

    public @NotNull Data union(@NotNull Data other) {
      return new Data(
        Math.min(startLine, other.startLine),
        Math.max(startCol, other.startCol),
        Math.max(endLine, other.endLine),
        Math.max(endCol, other.endCol)
      );
    }
  }
}
