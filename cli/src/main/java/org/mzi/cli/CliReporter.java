// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.cli;

import org.glavo.kala.collection.Seq;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.Problem;
import org.mzi.api.error.Reporter;

import java.nio.file.Path;

/**
 * @author ice1000
 */
public record CliReporter(@NotNull Path filePath) implements Reporter {
  @Override public void report(@NotNull Problem problem) {
    var errorMsg = StreamReporter.errorMsg(filePath, problem);
    (Seq.of(Problem.Severity.ERROR, Problem.Severity.WARN).contains(problem.level()) ? System.err : System.out)
      .println(errorMsg);
  }
}
