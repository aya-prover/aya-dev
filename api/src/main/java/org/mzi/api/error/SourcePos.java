// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.api.error;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.Global;

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
  /**
   * Single instance SourcePos for mocking tests and other usages.
   */
  public static final SourcePos NONE = new SourcePos(-1, -1, -1, -1, -1, -1);

  @Contract(pure = true) public int length() {
    return tokenEndIndex < tokenStartIndex
      ? 0
      : tokenEndIndex - tokenStartIndex + 1;
  }

  /**
   * Does tokenStartIndex tokenEndIndex before other.tokenStartIndex? May or may not be disjoint
   */
  @Contract(pure = true) public boolean startsBefore(@NotNull SourcePos other) {
    return tokenStartIndex < other.tokenStartIndex;
  }

  /**
   * Does this tokenStartIndex completely before other? Disjoint
   */
  @Contract(pure = true) public boolean startsBeforeDisjoint(@NotNull SourcePos other) {
    return tokenStartIndex < other.tokenStartIndex && tokenEndIndex < other.tokenStartIndex;
  }

  /**
   * Does this tokenStartIndex at or before other? Nondisjoint
   */
  @Contract(pure = true) public boolean startsBeforeNonDisjoint(@NotNull SourcePos other) {
    return tokenStartIndex <= other.tokenStartIndex && tokenEndIndex >= other.tokenStartIndex;
  }

  /**
   * Does tokenStartIndex tokenStartIndex after other.tokenEndIndex? May or may not be disjoint
   */
  @Contract(pure = true) public boolean startsAfter(@NotNull SourcePos other) {
    return tokenStartIndex > other.tokenStartIndex;
  }

  /**
   * Does this tokenStartIndex completely after other? Disjoint
   */
  @Contract(pure = true) public boolean startsAfterDisjoint(@NotNull SourcePos other) {
    return tokenStartIndex > other.tokenEndIndex;
  }

  /**
   * Does this tokenStartIndex after other? NonDisjoint
   */
  @Contract(pure = true) public boolean startsAfterNonDisjoint(@NotNull SourcePos other) {
    // this.tokenEndIndex >= other.tokenEndIndex implied
    return tokenStartIndex > other.tokenStartIndex && tokenStartIndex <= other.tokenEndIndex;
  }

  /**
   * Are both ranges disjoint? I.e., no overlap?
   */
  public boolean disjoint(@NotNull SourcePos other) {
    return startsBeforeDisjoint(other) || startsAfterDisjoint(other);
  }

  /**
   * Are two SourcePoss adjacent such as 0..41 and 42..42?
   */
  @Contract(pure = true) public boolean adjacent(@NotNull SourcePos other) {
    return tokenStartIndex == other.tokenEndIndex + 1 || tokenEndIndex == other.tokenStartIndex - 1;
  }

  @Contract(pure = true) public boolean properlyContains(@NotNull SourcePos other) {
    return other.tokenStartIndex >= tokenStartIndex && other.tokenEndIndex <= tokenEndIndex;
  }

  /**
   * Return the SourcePos computed from combining this and other
   */
  @Contract("_ -> new") public @NotNull SourcePos union(@NotNull SourcePos other) {
    return new SourcePos(
      Math.min(tokenStartIndex, other.tokenStartIndex),
      Math.max(tokenEndIndex, other.tokenEndIndex),
      Math.min(startLine, other.startLine),
      Math.max(startColumn, other.startColumn),
      Math.max(endLine, other.endLine),
      Math.max(endColumn, other.endColumn)
    );
  }

  /**
   * Return the SourcePos in common between this and other
   */
  @Contract("_ -> new") public @NotNull SourcePos intersection(@NotNull SourcePos other) {
    return new SourcePos(
      Math.max(tokenStartIndex, other.tokenStartIndex),
      Math.min(tokenEndIndex, other.tokenEndIndex),
      Math.max(startLine, other.startLine),
      Math.min(startColumn, other.startColumn),
      Math.min(endLine, other.endLine),
      Math.min(endColumn, other.endColumn)
    );
  }

  /**
   * Return the SourcePos with elements from this not in other;
   * other must not be totally enclosed (properly contained)
   * within this, which would result in two disjoint SourcePoss
   * instead of the single one returned by this method.
   */
  public @Nullable SourcePos differenceNotProperlyContained(@NotNull SourcePos other) {
    SourcePos diff = null;
    // other.tokenStartIndex to left of tokenStartIndex (or same)
    if (other.startsBeforeNonDisjoint(this)) {
      diff = new SourcePos(
        Math.max(tokenStartIndex, other.tokenEndIndex + 1),
        tokenEndIndex,
        startLine,
        startColumn,
        endLine,
        endColumn
      );
    }

    // other.tokenStartIndex to right of tokenStartIndex
    else if (other.startsAfterNonDisjoint(this)) {
      diff = new SourcePos(
        tokenStartIndex,
        other.tokenStartIndex - 1,
        startLine,
        startColumn,
        endLine,
        endColumn
      );
    }
    return diff;
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

  @Override
  public int hashCode() {
    // the equals() returns true in tests, so hashCode() should
    // be a constant according to JavaSE documentation.
    if (Global.isTest()) return 0;
    return Objects.hash(tokenStartIndex, tokenEndIndex, startLine, startColumn, endLine, endColumn);
  }
}
