// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.reporter;

import kala.collection.mutable.MutableList;
import org.jetbrains.annotations.NotNull;

public record DelayedReporter(
  @NotNull Reporter delegated,
  @NotNull MutableList<Problem> problems
) implements CollectingReporter, AutoCloseable {
  public DelayedReporter(@NotNull Reporter delegated) {
    this(delegated, MutableList.create());
  }

  @Override public void report(@NotNull Problem problem) {
    problems.append(problem);
  }

  public void reportNow() {
    delegated.reportAll(problems.view());
    problems.clear();
  }

  @Override public void close() { reportNow(); }
}
