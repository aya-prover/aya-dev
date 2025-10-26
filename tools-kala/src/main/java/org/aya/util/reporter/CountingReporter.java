// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.reporter;

import org.jetbrains.annotations.NotNull;

/// A mutable reporter that counts problems.
public interface CountingReporter extends Reporter {
  int problemSize(@NotNull Problem.Severity severity);

  default int errorSize() { return problemSize(Problem.Severity.ERROR); }
  default int warningSize() { return problemSize(Problem.Severity.WARN); }
  default int goalSize() { return problemSize(Problem.Severity.GOAL); }
  default boolean noError() { return errorSize() == 0 && goalSize() == 0; }
  default boolean anyError() { return !noError(); }
  default @NotNull String countToString() {
    return String.format("%d error(s), %d warning(s).", errorSize(), warningSize());
  }

  /** Forcibly wrap a reporter. */
  static @NotNull ClearableReporter delegate(@NotNull Reporter reporter) {
    return new ClearableReporter(reporter);
  }
}
