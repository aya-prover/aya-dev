// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.reporter;

import kala.collection.mutable.MutableList;
import org.jetbrains.annotations.NotNull;

public interface CollectingReporter extends CountingReporter {
  @NotNull MutableList<Problem> problems();

  @Override default int problemSize(Problem.@NotNull Severity severity) {
    return problems().count(it -> it.level() == severity);
  }

  @Override default void clear() {
    problems().clear();
  }

  static @NotNull CollectingReporter delegate(@NotNull Reporter delegate) {
    return new Delegated(delegate);
  }

  record Delegated(
    @NotNull Reporter delegated,
    @NotNull MutableList<Problem> problems
  ) implements CollectingReporter {
    public Delegated(@NotNull Reporter delegated) {
      this(delegated, MutableList.create());
    }

    @Override public void report(@NotNull Problem problem) {
      problems.append(problem);
      delegated.report(problem);
    }
  }
}
