// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.test;

import org.aya.api.error.Problem;
import org.aya.api.error.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.junit.jupiter.api.Assertions;

@TestOnly
public final class ThrowingReporter implements Reporter {
  public static final @NotNull ThrowingReporter INSTANCE = new ThrowingReporter();

  private ThrowingReporter() {
  }

  @Override public void report(@NotNull Problem problem) {
    if (problem.level() != Problem.Severity.ERROR) {
      System.err.println(problem.describe().renderWithPageWidth(120));
      return;
    }
    Assertions.fail("Failed with `" + problem.getClass() + "`: " + problem
      .describe()
      .renderWithPageWidth(120) + "\nat " + problem.sourcePos());
  }
}
