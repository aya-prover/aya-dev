// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.error;

import kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;

public record CollectingReporter(@NotNull Buffer<@NotNull Problem> problems) implements Reporter {
  public CollectingReporter() {
    this(Buffer.create());
  }

  @Override public void report(@NotNull Problem problem) {
    problems.append(problem);
  }
}
