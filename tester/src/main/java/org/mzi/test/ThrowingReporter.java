// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.test;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.junit.jupiter.api.Assertions;
import org.mzi.api.error.Problem;
import org.mzi.api.error.Reporter;
import org.mzi.pretty.backend.DocStringPrinter;

@TestOnly
public final class ThrowingReporter implements Reporter {
  public static final @NotNull ThrowingReporter INSTANCE = new ThrowingReporter();

  private ThrowingReporter() {
  }

  @Override public void report(@NotNull Problem problem) {
    Assertions.fail("Failed with `" + problem.getClass() + "`: " + problem
      .describe()
      .renderToString(new DocStringPrinter.Config(120)));
  }
}
