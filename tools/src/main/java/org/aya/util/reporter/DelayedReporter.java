// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.reporter;

import kala.collection.mutable.DynamicSeq;
import org.jetbrains.annotations.NotNull;

public record DelayedReporter(
  @NotNull Reporter delegated,
  @NotNull DynamicSeq<Problem> problems
) implements CollectingReporter, AutoCloseable {
  public DelayedReporter(@NotNull Reporter delegated) {
    this(delegated, DynamicSeq.create());
  }

  @Override public void report(@NotNull Problem problem) {
    problems.append(problem);
  }

  public void reportNow() {
    problems.forEach(this.delegated::report);
    problems.clear();
  }

  @Override public void close() {
    reportNow();
  }
}
