// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.reporter;

import kala.collection.mutable.MutableList;
import org.jetbrains.annotations.NotNull;

public record BufferReporter(@NotNull MutableList<@NotNull Problem> problems) implements CollectingReporter {
  public BufferReporter() {
    this(MutableList.create());
  }

  @Override public void report(@NotNull Problem problem) {
    problems.append(problem);
  }
}
