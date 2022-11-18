// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.reporter;

import org.aya.pretty.backend.string.StringPrinterConfig;
import org.aya.pretty.backend.string.style.AdaptiveCliStylist;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

@TestOnly
public record ThrowingReporter() implements CountingReporter {
  public static final @NotNull ThrowingReporter INSTANCE = new ThrowingReporter();

  public static @NotNull String errorMessage(
    @NotNull Problem problem, @NotNull DistillerOptions options,
    boolean unicode, boolean supportAnsi, int pageWidth
  ) {
    var doc = problem.sourcePos() == SourcePos.NONE ? problem.describe(options) : problem.toPrettyError(options).toDoc();
    if (supportAnsi) {
      var config = new StringPrinterConfig(AdaptiveCliStylist.INSTANCE, pageWidth, unicode);
      return doc.renderToString(config);
    }
    return doc.renderWithPageWidth(pageWidth, unicode);
  }

  @Override public void report(@NotNull Problem problem) {
    var render = errorMessage(problem, DistillerOptions.informative(), false, false, 80);
    if (problem.level() != Problem.Severity.ERROR) {
      System.err.println(render);
      return;
    }
    throw new AssertionError("Failed with `" + problem.getClass() + "`: " + render + "\nat " + problem.sourcePos());
  }

  @Override public int problemSize(Problem.@NotNull Severity severity) {
    return 0;
  }

  @Override public void clear() {
  }
}
