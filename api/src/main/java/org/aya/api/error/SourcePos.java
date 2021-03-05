// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.error;

import org.aya.api.Global;
import org.aya.pretty.error.LineColSpan;
import org.aya.pretty.error.RangeSpan;
import org.aya.pretty.error.Span;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Position in source code.
 * This class is usually constructed using antlr4's utility function
 * {@code ctx.getSourceInterval()}.
 *
 * @author kiva
 */
@SuppressWarnings("unused")
public record SourcePos(
  int tokenStartIndex,
  int tokenEndIndex,
  int startLine,
  int startColumn,
  int endLine,
  int endColumn
) {
  public static final int UNAVAILABLE_AND_FUCK_ANTLR4 = -114514;

  /**
   * Single instance SourcePos for mocking tests and other usages.
   */
  public static final SourcePos NONE = new SourcePos(-1, -1, -1, -1, -1, -1);

  public Span toSpan(@NotNull String input) {
    if (tokenStartIndex == UNAVAILABLE_AND_FUCK_ANTLR4
      || tokenEndIndex == UNAVAILABLE_AND_FUCK_ANTLR4) {
      return new LineColSpan(input, startLine, startColumn, endLine, endColumn);
    } else {
      return new RangeSpan(input, tokenStartIndex, tokenEndIndex);
    }
  }

  @Override public boolean equals(Object o) {
    // we return true when in tests because we
    // don't want to check source pos manually
    // as it is guaranteed to be correct by antlr.
    if (Global.isTest() || this == o) return true;
    if (!(o instanceof SourcePos sourcePos)) return false;
    return tokenStartIndex == sourcePos.tokenStartIndex &&
      tokenEndIndex == sourcePos.tokenEndIndex &&
      startLine == sourcePos.startLine &&
      startColumn == sourcePos.startColumn &&
      endLine == sourcePos.endLine &&
      endColumn == sourcePos.endColumn;
  }

  public boolean contains(int line, int column) {
    return line >= startLine && line <= endLine && column >= startColumn && column <= endColumn;
  }

  public boolean contains(int pos) {
    return pos >= tokenStartIndex && pos <= tokenEndIndex;
  }

  @Override
  public int hashCode() {
    // the equals() returns true in tests, so hashCode() should
    // be a constant according to JavaSE documentation.
    if (Global.isTest()) return 0;
    return Objects.hash(tokenStartIndex, tokenEndIndex, startLine, startColumn, endLine, endColumn);
  }
}
