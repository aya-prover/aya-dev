// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.Problem;
import org.aya.api.error.Reporter;
import org.aya.cli.single.CliReporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.PrintStream;

/**
 * @author ice1000
 */
@TestOnly
public record StreamReporter(@NotNull PrintStream stream) implements Reporter {
  @Override public void report(@NotNull Problem problem) {
    stream.println(CliReporter.errorMessage(problem, DistillerOptions.informative(), false, false, 80));
  }
}
