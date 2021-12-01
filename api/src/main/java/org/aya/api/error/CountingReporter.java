// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.api.error;

import kala.collection.mutable.MutableMap;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public final class CountingReporter implements Reporter {
  public final @NotNull Reporter delegated;
  private final @NotNull MutableMap<Problem.Severity, Integer> count = MutableMap.create();

  public CountingReporter(@NotNull Reporter delegated) {
    this.delegated = delegated;
  }

  public int errorSize() {
    return count.getOrDefault(Problem.Severity.ERROR, 0);
  }

  public int warningSize() {
    return count.getOrDefault(Problem.Severity.WARN, 0);
  }

  public boolean noError() {
    return errorSize() == 0;
  }

  public void clear() {
    count.clear();
  }

  @Override
  public @NotNull String countToString() {
    return String.format("%d error(s), %d warning(s).", errorSize(), warningSize());
  }

  @Override public void report(@NotNull Problem problem) {
    if (problem.sourcePos() != SourcePos.NONE) {
      count.put(problem.level(), count.getOrDefault(problem.level(), 0) + 1);
    }
    delegated.report(problem);
  }

  public static @NotNull CountingReporter of(@NotNull Reporter reporter) {
    return reporter instanceof CountingReporter counting
      ? counting : new CountingReporter(reporter);
  }
}
