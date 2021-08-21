// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.error;

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
      || end >= input.length()
      || end < start) {
      throw new IndexOutOfBoundsException();
    }

    return new RangeSpan(input, start, end);
  }

  private boolean isVariationSelector(int code) {
    return code >= (int) '\uFE00' && code <= (int) '\uFE0F';
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

    var codePoints = input.codePoints().toArray();
    while (pos < codePoints.length) {
      int c = codePoints[pos];
      int oldPos = pos++;
      int oldCol = col;

      if (c == '\n') {
        line++;
        col = 0;
      } else if (c == '\t') {
        // treat tab as tabWidth-length-ed spaces
        col += tabWidth;
      } else if (isVariationSelector(c)) {
        col += 0;
      } else if (c > 128 && (Character.isUnicodeIdentifierStart(c)
        || Character.isUnicodeIdentifierPart(c)
        || Character.isSupplementaryCodePoint(c))) {
        col += 2;
      } else {
        col += 1;
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
    return input.substring(start, end + 1);
  }
}
