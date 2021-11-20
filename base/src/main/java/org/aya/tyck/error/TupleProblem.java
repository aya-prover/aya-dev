package org.aya.tyck.error;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.Problem;
import org.aya.pretty.doc.Doc;
import org.aya.util.error.SourcePos;
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
