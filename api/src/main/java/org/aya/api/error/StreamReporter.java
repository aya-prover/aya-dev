// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.api.error;

import org.aya.api.distill.DistillerOptions;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;

/**
 * @author ice1000
 */
public record StreamReporter(@NotNull PrintStream stream) implements Reporter {
  @Override public void report(@NotNull Problem problem) {
    stream.println(problem.computeFullErrorMessage(DistillerOptions.informative(), false));
  }
}
