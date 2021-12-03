// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.Problem;
import org.aya.api.error.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.junit.jupiter.api.Assertions;

@TestOnly
public record ThrowingReporter() implements Reporter {
  public static final @NotNull ThrowingReporter INSTANCE = new ThrowingReporter();

  @Override public void report(@NotNull Problem problem) {
    var render = problem.computeFullErrorMessage(DistillerOptions.informative(), false);
    if (problem.level() != Problem.Severity.ERROR) {
      System.err.println(render);
      return;
    }
    Assertions.fail("Failed with `" + problem.getClass() + "`: " + render + "\nat " + problem.sourcePos());
  }
}
