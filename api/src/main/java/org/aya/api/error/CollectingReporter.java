// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.api.error;

import kala.collection.mutable.DynamicSeq;
import org.jetbrains.annotations.NotNull;

public interface CollectingReporter extends Reporter {
  @NotNull DynamicSeq<Problem> problems();

  default boolean anyError() {
    return problems().anyMatch(problem -> problem.level() == Problem.Severity.ERROR);
  }

  default boolean anyProblem() {
    return problems().isNotEmpty();
  }
}
