// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.error;

import org.jetbrains.annotations.NotNull;

public final class CountingReporter implements Reporter {
  public final @NotNull Reporter delegated;
  private int errors = 0;

  public CountingReporter(@NotNull Reporter delegated) {
    this.delegated = delegated;
  }

  public int size() {
    return errors;
  }

  public boolean isEmpty() {
    return size() == 0;
  }

  @Override public void report(@NotNull Problem problem) {
    if (problem.sourcePos() != SourcePos.NONE && problem.isError()) errors++;
    delegated.report(problem);
  }
}
