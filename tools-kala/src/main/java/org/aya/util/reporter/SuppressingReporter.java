// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.reporter;

import kala.collection.mutable.MutableSet;
import org.jetbrains.annotations.NotNull;

public record SuppressingReporter(
  @NotNull Reporter reporter,
  @NotNull MutableSet<Class<? extends Problem>> suppressed
) implements Reporter {
  public SuppressingReporter(@NotNull Reporter reporter) {
    this(reporter, MutableSet.create());
  }

  @Override public void report(@NotNull Problem problem) {
    if (suppressed.contains(problem.getClass())) return;
    reporter.report(problem);
  }

  public void suppress(@NotNull Class<? extends Problem> problem) {
    suppressed.add(problem);
  }

  public void clearSuppress() { suppressed.clear(); }
}
