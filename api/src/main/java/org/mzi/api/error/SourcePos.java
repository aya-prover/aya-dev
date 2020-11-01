// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.api.error;

import org.mzi.api.Global;

import java.util.Objects;

/**
 * Position in source code.
 * This class is usually constructed using antlr4's utility function
 * {@code ctx.getSourceInterval()}.
 *
 * @author kiva
 */
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

  public int length() {
    return tokenEndIndex < tokenStartIndex
      ? 0
      : tokenEndIndex - tokenStartIndex + 1;
  }

  /**
   * Does tokenStartIndex tokenEndIndex before other.tokenStartIndex? May or may not be disjoint
   */
  public boolean startsBefore(SourcePos other) {
    return tokenStartIndex < other.tokenStartIndex;
  }

  /**
   * Does this tokenStartIndex completely before other? Disjoint
   */
  public boolean startsBeforeDisjoint(SourcePos other) {
    return tokenStartIndex < other.tokenStartIndex && tokenEndIndex < other.tokenStartIndex;
  }

  /**
   * Does this tokenStartIndex at or before other? Nondisjoint
   */
  public boolean startsBeforeNonDisjoint(SourcePos other) {
    return tokenStartIndex <= other.tokenStartIndex && tokenEndIndex >= other.tokenStartIndex;
  }

  /**
   * Does tokenStartIndex tokenStartIndex after other.tokenEndIndex? May or may not be disjoint
   */
  public boolean startsAfter(SourcePos other) {
    return tokenStartIndex > other.tokenStartIndex;
  }

  /**
   * Does this tokenStartIndex completely after other? Disjoint
   */
  public boolean startsAfterDisjoint(SourcePos other) {
    return tokenStartIndex > other.tokenEndIndex;
  }

  /**
   * Does this tokenStartIndex after other? NonDisjoint
   */
  public boolean startsAfterNonDisjoint(SourcePos other) {
    // this.tokenEndIndex >= other.tokenEndIndex implied
    return tokenStartIndex > other.tokenStartIndex && tokenStartIndex <= other.tokenEndIndex;
  }

  /**
   * Are both ranges disjoint? I.e., no overlap?
   */
  public boolean disjoint(SourcePos other) {
    return startsBeforeDisjoint(other) || startsAfterDisjoint(other);
  }

  /**
   * Are two SourcePoss adjacent such as 0..41 and 42..42?
   */
  public boolean adjacent(SourcePos other) {
    return tokenStartIndex == other.tokenEndIndex + 1 || tokenEndIndex == other.tokenStartIndex - 1;
  }

  public boolean properlyContains(SourcePos other) {
    return other.tokenStartIndex >= tokenStartIndex && other.tokenEndIndex <= tokenEndIndex;
  }

  /**
   * Return the SourcePos computed from combining this and other
   */
  public SourcePos union(SourcePos other) {
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
  public SourcePos intersection(SourcePos other) {
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
  public SourcePos differenceNotProperlyContained(SourcePos other) {
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
    if (Global.TEST) return true;

    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SourcePos sourcePos = (SourcePos) o;
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
    if (Global.TEST) return 0;
    return Objects.hash(tokenStartIndex, tokenEndIndex, startLine, startColumn, endLine, endColumn);
  }
}
