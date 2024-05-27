// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test;

import org.aya.prettier.AyaPrettierOptions;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.PrintStream;

/**
 * @author ice1000
 */
@TestOnly
public record StreamReporter(@NotNull PrintStream stream) implements Reporter {
  @Override public void report(@NotNull Problem problem) {
    stream.println(Reporter.errorMessage(problem, AyaPrettierOptions.informative(), true, false, 80));
  }
}
