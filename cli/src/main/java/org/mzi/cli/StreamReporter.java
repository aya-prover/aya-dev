// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.cli;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.Problem;
import org.mzi.api.error.Reporter;
import org.mzi.api.error.SourcePos;
import org.mzi.pretty.doc.Doc;

import java.io.PrintStream;
import java.nio.file.Path;

/**
 * @author ice1000
 */
public record StreamReporter(@NotNull Path filePath, @NotNull PrintStream stream) implements Reporter {
  @Override public void report(@NotNull Problem problem) {
    var errorMsg = errorMsg(filePath, problem);
    stream.println(errorMsg);
  }

  public static @NotNull String errorMsg(@NotNull Path filePath, @NotNull Problem problem) {
    if (problem.sourcePos() == SourcePos.NONE)
      return problem.describe().renderWithPageWidth(114514);
    var error = problem.toPrettyError(filePath, Doc.empty()).toDoc();
    return error.renderWithPageWidth(80);
  }
}
