// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.single;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.Problem;
import org.aya.api.error.Reporter;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * @author ice1000
 */
public record CliReporter(@NotNull Consumer<String> out, @NotNull Consumer<String> err) implements Reporter {
  public static final CliReporter INSTANCE = new CliReporter(System.out::println, System.err::println);

  @Override public void report(@NotNull Problem problem) {
    var errorMsg = problem.computeFullErrorMessage(DistillerOptions.informative());
    var level = problem.level();
    if (problem.isError() || level == Problem.Severity.WARN) err.accept(errorMsg);
    else out.accept(errorMsg);
  }
}
