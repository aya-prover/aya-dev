// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.position;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineColumn;
import kala.collection.SeqView;
import org.aya.util.Global;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/// Position in source code for error reporting only.
///
/// @param tokenEndIndex   The index of last character (inclusive)
/// @param tokenStartIndex The index of first character (inclusive)
/// @author kiva
public record SourcePos(
  @NotNull SourceFile file,
  int tokenStartIndex,
  int tokenEndIndex,
  int startLine,
  int startColumn,
  int endLine,
  int endColumn
) implements Comparable<SourcePos> {
  public SourcePos {
    assert tokenEndIndex >= tokenStartIndex - 1;
  }

  /// Single instance SourcePos for mocking tests and other usages.
  public static final SourcePos NONE = new SourcePos(SourceFile.NONE, -1, -1, -1, -1, -1, -1);
  /// Source pos used in serialized core
  public static final SourcePos SER = new SourcePos(SourceFile.SER, -1, -1, -1, -1, -1, -1);

  private static int min(int x, int y) {
    if (x == -1) return y;
    if (y == -1) return x;
    return Math.min(x, y);
  }

  private static int max(int x, int y) {
    if (x == -1) return y;
    if (y == -1) return x;
    return Math.max(x, y);
  }

  @Contract("_ -> new") public @NotNull SourcePos union(@NotNull SourcePos other) {
    return new SourcePos(
      file,
      min(tokenStartIndex, other.tokenStartIndex),
      max(tokenEndIndex, other.tokenEndIndex),
      min(startLine, other.startLine),
      unionStartCol(other),
      max(endLine, other.endLine),
      unionEndCol(other)
    );
  }

  private int unionStartCol(@NotNull SourcePos other) {
    if (startLine == other.startLine) return min(startColumn, other.startColumn);
    if (startLine < other.startLine) return startColumn;
    return other.startColumn;
  }

  private int unionEndCol(@NotNull SourcePos other) {
    if (endLine == other.endLine) return max(endColumn, other.endColumn);
    if (endLine > other.endLine) return endColumn;
    return other.endColumn;
  }

  @Override public boolean equals(Object o) {
    // we return true when in tests because we
    // don't want to check source pos manually
    // as it is guaranteed to be correct by antlr.
    if (Global.UNITE_SOURCE_POS || this == o) return true;
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

  public boolean containsIndex(@NotNull SourcePos x) {
    return tokenStartIndex <= x.tokenStartIndex && tokenEndIndex >= x.tokenEndIndex;
  }

  public boolean belongsToSomeFile() { return this != SourcePos.NONE && file.isSomeFile(); }
  public int linesOfCode() { return endLine - startLine + 1; }
  public boolean oneLinear() { return startLine == endLine; }

  public @NotNull SourcePos sourcePosForSubExpr(@NotNull SourceFile sourceFile, @NotNull SeqView<SourcePos> params) {
    var restParamSourcePos = params.fold(SourcePos.NONE, (acc, it) -> {
      if (acc == SourcePos.NONE) return it;
      return new SourcePos(sourceFile, acc.tokenStartIndex(), it.tokenEndIndex(),
        acc.startLine(), acc.startColumn(), it.endLine(), it.endColumn());
    });
    return new SourcePos(
      sourceFile,
      restParamSourcePos.tokenStartIndex(),
      tokenEndIndex,
      restParamSourcePos.startLine(),
      restParamSourcePos.startColumn(),
      endLine,
      endColumn
    );
  }

  @Override public @NotNull String toString() {
    return "(" + tokenStartIndex + "-" + tokenEndIndex + ") [" + lineColumnString() + ']';
  }
  public @NotNull String lineColumnString() {
    return startLine + ":" + startColumn + "-" + endLine + ":" + endColumn;
  }

  @Override public int hashCode() {
    // the equals() returns true in tests, so hashCode() should
    // be a constant according to JLS
    if (Global.UNITE_SOURCE_POS) return 0;
    return Objects.hash(tokenStartIndex, tokenEndIndex, startLine, startColumn, endLine, endColumn);
  }

  /// Compare this [SourcePos] to given line-column position
  ///
  /// TODO: reverse the sign?
  /// @return * 0 if the position is contained in this [SourcePos]
  ///         * negative if the position is before this [SourcePos]
  ///         * positive if the position is after this [SourcePos]
  public int compareVisually(int line, int column) {
    var singleLine = startLine == endLine;
    var afterStartCol = startColumn <= column;
    var beforeEndCol = column <= endColumn;

    if (singleLine) {
      if (line != startLine) {
        // -1 if line < startLine
        // 1 if line > startLine
        // 0 if the computer corrupted
        return Integer.compare(line, startLine);
      }

      // same line
      return afterStartCol
        ? (beforeEndCol ? 0 : 1)
        : -1;
    }

    if (line == startLine) return afterStartCol ? 0 : -1;
    if (line == endLine) return beforeEndCol ? 0 : 1;

    // now, line != startLine != endLine
    return startLine < line
      ? (line < endLine ? 0 : 1)
      : -1;
  }

  @Override public int compareTo(@NotNull SourcePos o) { return Integer.compare(tokenStartIndex, o.tokenStartIndex); }
  public boolean isEmpty() { return length() <= 0; }
  private int length() { return tokenEndIndex - tokenStartIndex + 1; }
  public @NotNull SourcePos coalesceLeft() {
    return new SourcePos(file, tokenStartIndex, tokenStartIndex - 1,
      startLine, startColumn, startLine, startColumn);
  }

  public enum NowLoc {
    Shot, Start, End, Between, None,
  }

  public @NotNull NowLoc nowLoc(int currentLine) {
    if (currentLine == startLine) return oneLinear() ? NowLoc.Shot : NowLoc.Start;
    if (currentLine == endLine) return NowLoc.End;
    if (currentLine > startLine && currentLine < endLine) return NowLoc.Between;
    return NowLoc.None;
  }

  public static @NotNull SourcePos of(@NotNull TextRange range, @NotNull SourceFile file, boolean singleLine) {
    var start = offsetToLineColumn(file, range.getStartOffset(), 0);
    var length = range.getLength();
    var endOffset = range.getEndOffset() - (length == 0 ? 0 : 1);
    var end = singleLine || length == 0
      ? LineColumn.of(start.line, start.column + length - 1)
      : offsetToLineColumn(file, endOffset, start.line);
    return new SourcePos(file, range.getStartOffset(), endOffset,
      start.line + 1, start.column, end.line + 1, end.column);
  }

  public static @NotNull LineColumn offsetToLineColumn(@NotNull SourceFile file, int pos, int lowerBound) {
    var offsets = file.lineOffsets();
    var line = offsets.binarySearch(lowerBound, offsets.size(), pos);
    // We want `line` to be the last index in the array whose value is no greater than `pos`.
    // If `pos` exists in the array then `line = <index of pos>` from the binary search.
    // In the case where binary search does not find a direct result, `(-upperBound - 1)` is
    // returned. To obtain the value of `line` we subtract the index of `upperBound` by 1.
    if (line < 0) line = -line - 2;
    return LineColumn.of(line, pos - offsets.get(line));
  }
}
