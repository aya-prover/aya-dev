// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.reporter;

import kala.value.LazyValue;
import org.aya.util.PrettierOptions;
import org.jetbrains.annotations.NotNull;

public record ThrowingReporter(@NotNull LazyValue<PrettierOptions> options) implements CountingReporter {
  public ThrowingReporter(@NotNull PrettierOptions options) {
    this(LazyValue.ofValue(options));
  }

  @Override public void report(@NotNull Problem problem) {
    var render = Reporter.errorMessage(problem, options.get(), false, false, 80);
    if (!problem.isError()) {
      System.err.println(render);
      return;
    }
    throw new AssertionError("Failed with `" + problem.getClass() + "`: " + render + "\nat " + problem.sourcePos());
  }

  @Override public int problemSize(Problem.@NotNull Severity severity) { return 0; }
}
