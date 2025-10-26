// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.reporter;

import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public record ClearableReporter(@NotNull Reporter delegated, int @NotNull [] count) implements CountingReporter {
  public ClearableReporter(@NotNull Reporter delegated) {
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
