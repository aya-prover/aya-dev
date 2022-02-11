// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.pretty.doc.Doc;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

public sealed interface TupleProblem extends Problem {

  @Override default @NotNull Severity level() {
    return Severity.ERROR;
  }

  record ElemMismatchError(@NotNull SourcePos sourcePos,
                           int expected, int supplied
  ) implements TupleProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(Doc.english("Expected"),
        Doc.plain(String.valueOf(expected)),
        Doc.english("elements in the tuple, but found"),
        Doc.plain(String.valueOf(supplied)));
    }
  }
}
