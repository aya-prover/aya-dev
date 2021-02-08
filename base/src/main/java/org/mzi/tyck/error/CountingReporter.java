// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck.error;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.Problem;
import org.mzi.api.error.Reporter;

public final class CountingReporter implements Reporter {
  public final @NonNls Reporter delegated;
  private int errors = 0;

  public CountingReporter(@NonNls Reporter delegated) {
    this.delegated = delegated;
  }

  public int size() {
    return errors;
  }

  public boolean isEmpty() {
    return size() == 0;
  }

  @Override public void report(@NotNull Problem problem) {
    errors++;
    delegated.report(problem);
  }
}
