// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import kala.collection.Seq;
import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.api.util.WithPos;
import org.aya.concrete.Pattern;
import org.aya.core.pat.Pat;
import org.aya.core.term.Term;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface ClausesProblem extends Problem {
  @Override default @NotNull Severity level() {
    return Severity.ERROR;
  }

  private static @NotNull Doc termToHint(@Nullable Term term) {
    return term == null ? Doc.empty() : Doc.sep(Doc.english("substituted to"),
      Doc.styled(Style.code(), term.toDoc(DistillerOptions.DEFAULT)));
  }

  record Conditions(
    @Override @NotNull SourcePos sourcePos,
    int i, int j,
    @NotNull Term lhs, @Nullable Term rhs,
    @NotNull SourcePos conditionPos,
    @NotNull SourcePos iPos, @Nullable SourcePos jPos
  ) implements ClausesProblem {
    @Override public @NotNull Doc describe(DistillerOptions options) {
      var result = rhs != null ? Doc.sep(
        Doc.plain("unify"),
        Doc.styled(Style.code(), lhs.toDoc(options)),
        Doc.plain("and"),
        Doc.styled(Style.code(), rhs.toDoc(options))
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
    @Override @NotNull SourcePos sourcePos,
    int i, int j,
    @NotNull Term lhs, @NotNull Term rhs,
    @NotNull SourcePos iPos, @NotNull SourcePos jPos
  ) implements ClausesProblem {
    @Override public @NotNull Doc describe(DistillerOptions options) {
      return Doc.vcat(
        Doc.sep(
          Doc.plain("The"),
          Doc.ordinal(i),
          Doc.english("and the"),
          Doc.ordinal(j),
          Doc.english("clauses are not confluent because we failed to unify")),
        Doc.par(1, lhs.toDoc(options)),
        Doc.plain("and"),
        Doc.par(1, rhs.toDoc(options))
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
  record MissingCase(
    @Override @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Pattern> pats
  ) implements ClausesProblem {
    @Override public @NotNull Doc describe(DistillerOptions options) {
      return Doc.sep(Doc.english("Unhandled case:"), Doc.commaList(pats.map(t -> t.toDoc(options))));
    }
  }

  record SplitInterval(@Override @NotNull SourcePos sourcePos, @NotNull Pat pat) implements ClausesProblem {
    @Override public @NotNull Doc describe(DistillerOptions options) {
      return Doc.sep(
        Doc.english("Cannot perform pattern matching"),
        Doc.styled(Style.code(), pat.toDoc(options))
      );
    }
  }
}
