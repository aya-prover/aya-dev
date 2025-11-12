// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.reporter;

import org.jetbrains.annotations.NotNull;

public final class LocalReporter implements Reporter {
  private final @NotNull Reporter parent;
  private boolean dirty = false;

  public LocalReporter(@NotNull Reporter parent) { this.parent = parent; }

  @Override
  public void report(@NotNull Problem problem) {
    if (problem.isError()) dirty = true;
    parent.report(problem);
  }

  public boolean dirty() {
    return dirty;
  }
}
