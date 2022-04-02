// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test;

import org.aya.util.distill.DistillerOptions;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
import org.aya.util.reporter.ThrowingReporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.PrintStream;

/**
 * @author ice1000
 */
@TestOnly
public record StreamReporter(@NotNull PrintStream stream) implements Reporter {
  @Override public void report(@NotNull Problem problem) {
    stream.println(ThrowingReporter.errorMessage(problem, DistillerOptions.informative(), false, false, 80));
  }
}
