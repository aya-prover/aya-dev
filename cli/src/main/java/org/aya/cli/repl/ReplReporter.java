// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli.repl;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.Problem;
import org.aya.api.error.Reporter;
import org.jetbrains.annotations.NotNull;


public final record ReplReporter(AbstractRepl repl) implements Reporter {
  /**
   * Copied and adapted.
   *
   * @see org.aya.cli.single.CliReporter#report
   */
  @Override public void report(@NotNull Problem problem) {
    var errorMsg = problem.computeFullErrorMessage(DistillerOptions.DEFAULT);
    var level = problem.level();
    if (problem.isError() || level == Problem.Severity.WARN) repl.errPrintln(errorMsg);
    else repl.println(errorMsg);
  }
}
