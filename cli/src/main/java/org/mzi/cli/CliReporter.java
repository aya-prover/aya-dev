// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.cli;

import org.glavo.kala.collection.Seq;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.Problem;
import org.mzi.api.error.Reporter;
import org.mzi.pretty.doc.Doc;

import java.nio.file.Path;

/**
 * @author ice1000
 */
public record CliReporter(@NotNull Path filePath) implements Reporter {
  @Override public void report(@NotNull Problem problem) {
    var error = problem.toPrettyError(filePath, Doc.empty()).toDoc();
    var errorMsg = error.renderWithPageWidth(80); // TODO[kiva]: get terminal width
    (Seq.of(Problem.Severity.ERROR, Problem.Severity.WARN).contains(problem.level()) ? System.err : System.out)
      .println(errorMsg);
  }
}
