// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.reporter;

import kala.collection.mutable.MutableList;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public interface CollectingReporter extends CountingReporter {
  @NotNull MutableList<Problem> problems();

  @Override default int problemSize(Problem.@NotNull Severity severity) {
    return problems().count(it -> it.level() == severity);
  }

  @Override default void clear() {
    problems().clear();
  }

  @Override default void raiseError() {
    problems().append(Reporter.dummyProblem(Doc.empty(), Problem.Severity.ERROR));
  }
}
