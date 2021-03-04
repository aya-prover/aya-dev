// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli;

import org.aya.api.error.Problem;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.nio.file.Path;

/**
 * @author ice1000
 */
public record StreamReporter(@NotNull Path filePath, @NotNull String sourceCode, @NotNull PrintStream stream) implements Reporter {
  @Override public void report(@NotNull Problem problem) {
    var errorMsg = errorMsg(filePath, sourceCode, problem);
    stream.println(errorMsg);
  }

  public static @NotNull String errorMsg(@NotNull Path filePath, @NotNull String sourceCode, @NotNull Problem problem) {
    if (problem.sourcePos() == SourcePos.NONE)
      return problem.describe().renderWithPageWidth(114514);
    var error = problem.toPrettyError(filePath, Doc.empty(), sourceCode).toDoc();
    return error.renderWithPageWidth(80);
  }
}
