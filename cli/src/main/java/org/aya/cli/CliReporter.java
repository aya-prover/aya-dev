// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli;

import org.aya.api.error.Problem;
import org.aya.api.error.Reporter;
import org.glavo.kala.collection.Seq;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * @author ice1000
 */
public record CliReporter(@NotNull Path filePath, @NotNull String sourceCode) implements Reporter {
  @Override public void report(@NotNull Problem problem) {
    var errorMsg = StreamReporter.errorMsg(filePath, sourceCode, problem);
    (Seq.of(Problem.Severity.ERROR, Problem.Severity.WARN).contains(problem.level()) ? System.err : System.out)
      .println(errorMsg);
  }
}
