// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.reporter;

import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/// A mutable reporter that counts problems.
public interface CountingReporter extends Reporter {
  int problemSize(@NotNull Problem.Severity severity);

  default int errorSize() { return problemSize(Problem.Severity.ERROR); }
  default int warningSize() { return problemSize(Problem.Severity.WARN); }
  default int goalSize() { return problemSize(Problem.Severity.GOAL); }
  default boolean noError() { return errorSize() == 0 && goalSize() == 0; }
  default boolean anyError() { return !noError(); }
  default @NotNull String countToString() {
    return String.format("%d error(s), %d warning(s).", errorSize(), warningSize());
  }

  /** Forcibly wrap a reporter. */
  static @NotNull CountingReporter.Delegated delegate(@NotNull Reporter reporter) {
    return new Delegated(reporter);
  }

  record Delegated(@NotNull Reporter delegated, int @NotNull [] count) implements CountingReporter {
    public Delegated(@NotNull Reporter delegated) {
      this(delegated, new int[Problem.Severity.class.getEnumConstants().length]);
    }

    @Override public int problemSize(Problem.@NotNull Severity severity) {
      return count[severity.ordinal()];
    }

    public void clearCounts() {
      Arrays.fill(count, 0);
    }

    @Override public void report(@NotNull Problem problem) {
      if (problem.sourcePos() != SourcePos.NONE) count[problem.level().ordinal()]++;
      delegated.report(problem);
    }
  }
}
