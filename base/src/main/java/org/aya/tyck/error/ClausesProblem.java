// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import kala.collection.Seq;
import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.api.util.WithPos;
import org.aya.core.pat.Pat;
import org.aya.core.term.Term;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.tyck.pat.PatTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface ClausesProblem extends Problem {
  @Override default @NotNull Severity level() {
    return Severity.ERROR;
  }

  private static @NotNull Doc termToHint(@Nullable Term term) {
    return term == null ? Doc.empty() : Doc.sep(Doc.plain("substituted to"), Doc.styled(Style.code(), term.toDoc()));
  }

  record Conditions(
    @NotNull SourcePos sourcePos,
    int i, int j,
    @NotNull Term lhs, @Nullable Term rhs,
    @NotNull SourcePos conditionPos,
    @NotNull SourcePos iPos, @Nullable SourcePos jPos
  ) implements ClausesProblem {
    @Override public @NotNull Doc describe() {
      var result = rhs != null ? Doc.sep(
        Doc.plain("unify"),
        Doc.styled(Style.code(), lhs.toDoc()),
        Doc.plain("and"),
        Doc.styled(Style.code(), rhs.toDoc())
      ) : Doc.english("even reduce one of the clause(s) to check condition");
      return Doc.sep(
        Doc.plain("The"),
        Doc.ordinal(i),
        Doc.english("clause matches on a constructor with condition(s). When checking the"),
        Doc.ordinal(j),
        Doc.english("condition, we failed to"),
        result
      );
    }

    @Override public @NotNull SeqLike<WithPos<Doc>> inlineHints() {
      var view = Seq.of(
        new WithPos<>(conditionPos, Doc.plain("relevant condition")),
        new WithPos<>(iPos, termToHint(lhs))).view();
      return rhs == null || jPos == null ? view : view.concat(Seq.of(new WithPos<>(jPos, termToHint(rhs))));
    }
  }

  record Confluence(
    @NotNull SourcePos sourcePos,
    int i, int j,
    @NotNull Term lhs, @NotNull Term rhs,
    @NotNull SourcePos iPos, @NotNull SourcePos jPos
  ) implements ClausesProblem {
    @Override public @NotNull Doc describe() {
      return Doc.sep(
        Doc.plain("The"),
        Doc.ordinal(i),
        Doc.english("and the"),
        Doc.ordinal(j),
        Doc.english("clauses are not confluent because we failed to unify"),
        Doc.styled(Style.code(), lhs.toDoc()),
        Doc.plain("and"),
        Doc.styled(Style.code(), rhs.toDoc())
      );
    }

    @Override public @NotNull Seq<WithPos<Doc>> inlineHints() {
      return Seq.of(new WithPos<>(iPos, termToHint(lhs)),
        new WithPos<>(jPos, termToHint(rhs)));
    }
  }

  /**
   * @author ice1000
   */
  record MissingCase(@NotNull SourcePos sourcePos, @NotNull ImmutableSeq<PatTree> pats) implements ClausesProblem {
    @Override public @NotNull Doc describe() {
      return Doc.sep(Doc.english("Unhandled case:"), Doc.join(Doc.plain(", "), pats.map(PatTree::toDoc)));
    }
  }

  record SplitInterval(@NotNull SourcePos sourcePos, @NotNull Pat pat) implements ClausesProblem {
    @Override public @NotNull Doc describe() {
      return Doc.sep(
        Doc.english("Cannot perform pattern matching"),
        Doc.styled(Style.code(), pat.toDoc())
      );
    }
  }
}
