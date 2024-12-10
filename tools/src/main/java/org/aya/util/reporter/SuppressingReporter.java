// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.reporter;

import kala.collection.mutable.MutableList;
import org.jetbrains.annotations.NotNull;

public record SuppressingReporter(
  @NotNull Reporter reporter,
  @NotNull MutableList<Class<? extends Problem>> suppressed
) implements Reporter {
  public SuppressingReporter(@NotNull Reporter reporter) {
    this(reporter, MutableList.create());
  }

  @Override public void report(@NotNull Problem problem) {
    if (suppressed.contains(problem.getClass())) return;
    reporter.report(problem);
  }

  public void suppress(@NotNull Class<? extends Problem> problem) {
    suppressed.append(problem);
  }
}
