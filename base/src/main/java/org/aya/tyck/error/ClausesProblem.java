// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.core.pat.Pat;
import org.aya.core.term.Term;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.pat.PatTree;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface ClausesProblem extends Problem {
  @Override default @NotNull Severity level() {
    return Severity.ERROR;
  }

  record Conditions(
    @NotNull SourcePos sourcePos,
    int i, int j,
    @NotNull Term lhs, @Nullable Term rhs
  ) implements ClausesProblem {
    @Override public @NotNull Doc describe() {
      var result = rhs != null ? Doc.hcat(
        Doc.plain("unify `"),
        lhs.toDoc(),
        Doc.plain("` and `"),
        rhs.toDoc(),
        Doc.plain("`")
      ) : Doc.plain("even reduce one of the clause(s) to check condition");
      return Doc.hcat(
        Doc.plain("The "),
        Doc.ordinal(i),
        Doc.plain(" clause matches on a constructor with condition(s). When checking the "),
        Doc.ordinal(j),
        Doc.plain(" condition, we failed to "),
        result
      );
    }
  }

  record Confluence(
    @NotNull SourcePos sourcePos,
    int i, int j,
    @NotNull Term lhs, @NotNull Term rhs
  ) implements ClausesProblem {
    @Override public @NotNull Doc describe() {
      return Doc.hcat(
        Doc.plain("The "),
        Doc.ordinal(i),
        Doc.plain(" and the "),
        Doc.ordinal(j),
        Doc.plain(" clauses are not confluent because we failed to unify `"),
        lhs.toDoc(),
        Doc.plain("` and `"),
        rhs.toDoc(),
        Doc.plain("`")
      );
    }
  }

  /**
   * @author ice1000
   */
  record MissingCase(@NotNull SourcePos sourcePos, @NotNull Buffer<PatTree> pats) implements ClausesProblem {
    @Override public @NotNull Doc describe() {
      return Doc.hcat(
        Doc.plain("Unhandled case: "),
        Doc.join(Doc.plain(", "), pats.stream().map(PatTree::toDoc))
      );
    }
  }

  record SplitInterval(@NotNull SourcePos sourcePos, @NotNull Pat pat) implements ClausesProblem {
    @Override public @NotNull Doc describe() {
      return Doc.hcat(
        Doc.plain("Cannot perform pattern matching `"),
        pat.toDoc(),
        Doc.plain("`")
      );
    }
  }
}
