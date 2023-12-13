// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.error;

import kala.collection.SeqView;
import org.aya.pretty.error.LineColSpan;
import org.aya.pretty.error.Span;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Position in source code for error reporting only.
 *
 * @param tokenEndIndex   The index of last character (inclusive)
 * @param tokenStartIndex The index of first character (inclusive)
 * @author kiva
 */
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
    assert tokenEndIndex >= tokenStartIndex;
  }

  /** Single instance SourcePos for mocking tests and other usages. */
  public static final SourcePos NONE = new SourcePos(SourceFile.NONE, -1, -1, -1, -1, -1, -1);
  /** Source pos used in serialized core */
  public static final SourcePos SER = new SourcePos(SourceFile.SER, -1, -1, -1, -1, -1, -1);

  public @NotNull Span toSpan() {
    return new LineColSpan(file().sourceCode(), startLine, startColumn, endLine, endColumn);
  }

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

  public boolean containsVisually(int line, int column) {
    return line >= startLine && line <= endLine && column >= startColumn - 1 && column <= endColumn;
  }

  public boolean contains(int pos) {
    return pos >= tokenStartIndex && pos <= tokenEndIndex;
  }

  public boolean containsIndex(@NotNull SourcePos x) {
    return tokenStartIndex <= x.tokenStartIndex && tokenEndIndex >= x.tokenEndIndex;
  }

  public boolean belongsToSomeFile() {
    return this != SourcePos.NONE && file.isSomeFile();
  }

  public int linesOfCode() {
    return endLine - startLine + 1;
  }

  public @NotNull SourcePos sourcePosForSubExpr(@NotNull SeqView<SourcePos> params) {
    return sourcePosForSubExpr(file, params);
  }

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

  @Override public String toString() {
    return STR."(\{tokenStartIndex}-\{tokenEndIndex}) [\{startLine},\{startColumn}-\{endLine},\{endColumn}\{']'}";
  }

  @Override
  public int hashCode() {
    // the equals() returns true in tests, so hashCode() should
    // be a constant according to JLS
    if (Global.UNITE_SOURCE_POS) return 0;
    return Objects.hash(tokenStartIndex, tokenEndIndex, startLine, startColumn, endLine, endColumn);
  }

  @Override public int compareTo(@NotNull SourcePos o) {
    return Integer.compare(tokenStartIndex, o.tokenStartIndex);
  }

  public boolean isEmpty() {
    return size() <= 0;
  }

  private int size() {
    return tokenEndIndex - tokenStartIndex + 1;
  }
}
