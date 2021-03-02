// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.pretty.error;

import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
public record RangeSpan(
  @NotNull String input,
  int start,
  int end
) implements Span {
  public static @NotNull RangeSpan from(@NotNull String input, int start, int end) {
    if (start < 0 || end < 0
      || start >= input.length()
      || end >= input.length()
      || end < start) {
      throw new IndexOutOfBoundsException();
    }

    return new RangeSpan(input, start, end);
  }

  @Override
  public @NotNull Span.Data normalize(PrettyErrorConfig config) {
    String input = input();
    int line = 1;
    int col = 0;
    int pos = 0;

    int startLine = -1;
    int startCol = -1;
    int endLine = -1;
    int endCol = -1;

    int tabWidth = config.tabWidth();

    for (char c : input.toCharArray()) {
      int oldPos = pos++;
      int oldCol = col;
      switch (c) {
        case '\n' -> {
          line++;
          col = 0;
        }
        // treat tab as tabWidth-length-ed spaces
        case '\t' -> col += tabWidth;
        default -> col++;
      }

      if (oldPos == start) {
        startLine = line;
        startCol = oldCol;
      }
      if (oldPos == end) {
        endLine = line;
        endCol = oldCol;
      }
    }

    return new Data(startLine, startCol, endLine, endCol);
  }

  @Override public String toString() {
    return input.substring(start, end);
  }
}
