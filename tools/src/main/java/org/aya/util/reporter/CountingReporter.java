// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.reporter;

import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public interface CountingReporter extends Reporter {
  int problemSize(@NotNull Problem.Severity severity);
  void clear();

  default int errorSize() {
    return problemSize(Problem.Severity.ERROR);
  }

  default int warningSize() {
    return problemSize(Problem.Severity.WARN);
  }

  default boolean noError() {
    return errorSize() == 0;
  }

  default boolean anyError() {
    return !noError();
  }

  default @NotNull String countToString() {
    return String.format("%d error(s), %d warning(s).", errorSize(), warningSize());
  }

  /** Wrap a reporter if it is not a counting reporter. */
  static @NotNull CountingReporter of(@NotNull Reporter reporter) {
    return reporter instanceof CountingReporter counting ? counting : delegate(reporter);
  }

  /** Forcibly wrap a reporter. */
  static @NotNull CountingReporter delegate(@NotNull Reporter reporter) {
    return new Delegated(reporter);
  }

  record Delegated(
    @NotNull Reporter delegated,
    int @NotNull [] count
  ) implements CountingReporter {
    public Delegated(@NotNull Reporter delegated) {
      this(delegated, new int[Problem.Severity.class.getEnumConstants().length]);
    }

    @Override public int problemSize(Problem.@NotNull Severity severity) {
      return count[severity.ordinal()];
    }

    @Override public void clear() {
      Arrays.fill(count, 0);
    }

    @Override public void report(@NotNull Problem problem) {
      if (problem.sourcePos() != SourcePos.NONE) count[problem.level().ordinal()]++;
      delegated.report(problem);
    }
  }
}
