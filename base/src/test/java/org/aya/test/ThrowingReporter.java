// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test;

import org.aya.cli.single.CliReporter;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.junit.jupiter.api.Assertions;

@TestOnly
public record ThrowingReporter() implements Reporter {
  public static final @NotNull ThrowingReporter INSTANCE = new ThrowingReporter();

  @Override public void report(@NotNull Problem problem) {
    var render = CliReporter.errorMessage(problem, DistillerOptions.informative(), false, false, 80);
    if (problem.level() != Problem.Severity.ERROR) {
      System.err.println(render);
      return;
    }
    Assertions.fail("Failed with `" + problem.getClass() + "`: " + render + "\nat " + problem.sourcePos());
  }
}
