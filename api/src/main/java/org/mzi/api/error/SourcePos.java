package org.mzi.api.error;

/**
 * Position in source code.
 * This class is usually constructed using antlr4's utility function
 * {@code ctx.getSourceInterval()}.
 *
 * @author kiva
 */
public record SourcePos(
  int start,
  int end,
  int startLine,
  int startColumn
) {
  /**
   * Single instance SourcePos for mocking tests and other usages.
   */
  public static final SourcePos NONE = new SourcePos(-1, -1, -1, -1);

  public int length() {
    return end < start
      ? 0
      : end - start + 1;
  }

  /**
   * Does start end before other.start? May or may not be disjoint
   */
  public boolean startsBefore(SourcePos other) {
    return start < other.start;
  }

  /**
   * Does this start completely before other? Disjoint
   */
  public boolean startsBeforeDisjoint(SourcePos other) {
    return start < other.start && end < other.start;
  }

  /**
   * Does this start at or before other? Nondisjoint
   */
  public boolean startsBeforeNonDisjoint(SourcePos other) {
    return start <= other.start && end >= other.start;
  }

  /**
   * Does start start after other.end? May or may not be disjoint
   */
  public boolean startsAfter(SourcePos other) {
    return start > other.start;
  }

  /**
   * Does this start completely after other? Disjoint
   */
  public boolean startsAfterDisjoint(SourcePos other) {
    return start > other.end;
  }

  /**
   * Does this start after other? NonDisjoint
   */
  public boolean startsAfterNonDisjoint(SourcePos other) {
    // this.end >= other.end implied
    return start > other.start && start <= other.end;
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
    return start == other.end + 1 || end == other.start - 1;
  }

  public boolean properlyContains(SourcePos other) {
    return other.start >= start && other.end <= end;
  }

  /**
   * Return the SourcePos computed from combining this and other
   */
  public SourcePos union(SourcePos other) {
    return new SourcePos(
      Math.min(start, other.start),
      Math.max(end, other.end),
      Math.min(startLine, other.startLine),
      Math.max(startColumn, other.startColumn)
    );
  }

  /**
   * Return the SourcePos in common between this and other
   */
  public SourcePos intersection(SourcePos other) {
    return new SourcePos(
      Math.max(start, other.start),
      Math.min(end, other.end),
      Math.max(startLine, other.startLine),
      Math.min(startColumn, other.startColumn)
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
    // other.start to left of start (or same)
    if (other.startsBeforeNonDisjoint(this)) {
      diff = new SourcePos(
        Math.max(start, other.end + 1),
        end,
        startLine,
        startColumn
      );
    }

    // other.start to right of start
    else if (other.startsAfterNonDisjoint(this)) {
      diff = new SourcePos(
        start,
        other.start - 1,
        startLine,
        startColumn
      );
    }
    return diff;
  }
}
