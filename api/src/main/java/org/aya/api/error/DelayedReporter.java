// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.error;

import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;

public record DelayedReporter(
  @NotNull Reporter delegated,
  @NotNull Buffer<Problem> problems
) implements Reporter, AutoCloseable {
  public DelayedReporter(@NotNull Reporter delegated) {
    this(delegated, Buffer.of());
  }

  @Override public void report(@NotNull Problem problem) {
    problems.append(problem);
  }

  public void reportNow() {
    problems.forEach(this.delegated::report);
    problems.clear();
  }

  @Override public void close() {
    reportNow();
  }
}
