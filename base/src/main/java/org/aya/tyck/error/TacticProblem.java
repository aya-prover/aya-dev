// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.concrete.Expr;
import org.aya.concrete.TacNode;
import org.aya.pretty.doc.Doc;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

/**
 * @author Luna
 */
public sealed interface TacticProblem extends Problem {
  @Override default @NotNull Problem.Severity level() {
    return Severity.ERROR;
  }

  record HoleFillerNumberMismatch(@NotNull SourcePos sourcePos,
                                  int expected, int supplied) implements TacticProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(Doc.english("Expected"),
        Doc.plain(String.valueOf(expected)),
        Doc.english("filler(s) in the tactic block, but found"),
        Doc.plain(String.valueOf(supplied)));
    }
  }

  record TacHeadCannotBeList(@NotNull SourcePos sourcePos,
                             @NotNull TacNode.ListExprTac tacList) implements TacticProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.vcat(Doc.english("Tactic head of"),
        tacList.toDoc(options),
        Doc.english("cannot be a list"));
    }
  }

  record NestedTactic(@NotNull SourcePos sourcePos, @NotNull Expr.TacExpr outer,
                      @NotNull Expr.TacExpr inner) implements TacticProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.vcat(Doc.par(1, inner.toDoc(options)),
        Doc.english("is nested in"),
        Doc.par(1, outer.toDoc(options)));
    }
  }

  record HoleFillerCannotHaveHole(@NotNull SourcePos sourcePos, @NotNull TacNode.ExprTac exprTac) implements TacticProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.english("Hole filler cannot have holes");
    }
  }

}
