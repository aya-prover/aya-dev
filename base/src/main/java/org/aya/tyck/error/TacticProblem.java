// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.concrete.Expr;
import org.aya.pretty.doc.Doc;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

/**
 * @author luna
 */
public interface TacticProblem extends Problem {

  @Override default @NotNull Problem.Severity level() {
    return Severity.ERROR;
  }

  record HoleNumberMismatchError(@NotNull SourcePos sourcePos,
                                 int expected, int supplied) implements TacticProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(Doc.english("Expected"),
        Doc.plain(String.valueOf(expected)),
        Doc.english("holes in the tactic head, but found"),
        Doc.plain(String.valueOf(supplied)));
    }
  }

  record TacHeadCannotBeList(@NotNull SourcePos sourcePos, @NotNull Expr.ListExprTac tacList) implements TacticProblem {

    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(Doc.english("Tactic head of"),
        tacList.toDoc(options),
        Doc.english("cannot be a list"));
    }
  }

}
