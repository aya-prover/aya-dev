// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.reporter;

import kala.collection.mutable.DynamicSeq;
import org.jetbrains.annotations.NotNull;

public interface CollectingReporter extends CountingReporter {
  @NotNull DynamicSeq<Problem> problems();

  @Override default int problemSize(Problem.@NotNull Severity severity) {
    return problems().count(it -> it.level() == severity);
  }

  @Override default void clear() {
    problems().clear();
  }
}
