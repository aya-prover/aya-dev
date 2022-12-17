// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.core.def.CtorDef;
import org.aya.core.term.DataCall;
import org.aya.core.term.Term;
import org.aya.pretty.BasePrettier;
import org.aya.generic.util.NormalizeMode;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.TyckState;
import org.aya.tyck.error.UnifyError;
import org.aya.tyck.unify.TermComparator;
import org.aya.util.Arg;
import org.aya.util.pretty.PrettierOptions;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface ClausesProblem extends Problem {
  @Override default @NotNull Severity level() {
    return Severity.ERROR;
  }

  private static @NotNull Doc termToHint(@Nullable Term term, @NotNull PrettierOptions options) {
    return term == null ? Doc.empty() : Doc.sep(Doc.english("substituted to"),
      Doc.code(term.toDoc(options)));
  }

  record CondData(
    int i, int j,
    @NotNull ImmutableSeq<Arg<Term>> args,
    @NotNull Term lhs,
    @NotNull TyckState state,
    @NotNull SourcePos iPos
  ) {
  }

  record Conditions(
    @Override @NotNull SourcePos sourcePos,
    @NotNull CondData data, @Nullable Term rhs,
    @Nullable SourcePos jPos
  ) implements ClausesProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      var result = rhs != null ? Doc.sep(
        Doc.plain("unify"),
        Doc.code(data.lhs.toDoc(options)),
        Doc.plain("and"),
        Doc.code(rhs.toDoc(options))
      ) : Doc.english("find any of the clause(s) to check condition");
      var line = Doc.sep(
        Doc.plain("The"),
        Doc.ordinal(data.i),
        Doc.english("clause matches on a constructor with condition(s). When checking the"),
        Doc.ordinal(data.j),
        Doc.english("condition, we failed to"),
        result,
        Doc.english("for the arguments:")
      );
      return Doc.vcat(line,
        Doc.par(1, BasePrettier.argsDoc(options, data.args)),
        Doc.english("Normalized:"),
        Doc.par(1, BasePrettier.argsDoc(options, data.args.map(a ->
          a.descent(t -> t.normalize(data.state, NormalizeMode.NF))))));
    }

    @Override public @NotNull SeqView<WithPos<Doc>> inlineHints(@NotNull PrettierOptions options) {
      var view = Seq.of(
        // new WithPos<>(conditionPos, Doc.plain("relevant condition")),
        new WithPos<>(data.iPos, termToHint(data.lhs, options))).view();
      return rhs == null || jPos == null ? view : view.concat(Seq.of(new WithPos<>(jPos, termToHint(rhs, options))));
    }
  }

  record Confluence(
    @Override @NotNull SourcePos sourcePos,
    int i, int j,
    @NotNull Term lhs, @NotNull Term rhs,
    @Override @NotNull TyckState state,
    @Override @NotNull TermComparator.FailureData failureData,
    @NotNull SourcePos iPos, @NotNull SourcePos jPos
  ) implements ClausesProblem, UnifyError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      var line = Doc.sep(
        Doc.plain("The"),
        Doc.ordinal(i),
        Doc.english("and the"),
        Doc.ordinal(j),
        Doc.english("clauses are not confluent because we failed to unify"));
      return describeUnify(options, line, lhs, Doc.plain("and"), rhs);
    }

    @Override public @NotNull Severity level() {
      return ClausesProblem.super.level();
    }

    @Override public @NotNull SeqView<WithPos<Doc>> inlineHints(@NotNull PrettierOptions options) {
      return Seq.of(new WithPos<>(iPos, termToHint(lhs, options)),
        new WithPos<>(jPos, termToHint(rhs, options))).view();
    }
  }

  /**
   * @author ice1000
   */
  record MissingCase(
    @Override @NotNull SourcePos sourcePos,
    @NotNull PatClassifier.PatErr pats
  ) implements ClausesProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.english("Unhandled case:"),
        BasePrettier.argsDoc(options, pats.missing()));
    }
  }

  record UnsureCase(
    @Override @NotNull SourcePos sourcePos,
    @NotNull CtorDef ctor,
    @NotNull DataCall dataCall
  ) implements ClausesProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.vcat(
        // Use `unsure` instead of `not sure`, which is used in Agda
        Doc.english("I'm unsure if there should be a case for constructor"),
        Doc.par(1, ctor.toDoc(options)),
        Doc.english("because I got stuck on the index unification of type"),
        Doc.par(1, dataCall.toDoc(options))
      );
    }
  }

  record Domination(int dom, int sub, @Override @NotNull SourcePos sourcePos) implements ClausesProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      var subOrdinal = Doc.ordinal(sub);
      return Doc.sep(
        Doc.english("The"), Doc.ordinal(dom),
        Doc.english("clause dominates the"), subOrdinal,
        Doc.english("clause. The"), subOrdinal,
        Doc.english("clause will be unreachable")
      );
    }

    @Override public @NotNull Severity level() {
      return Severity.WARN;
    }
  }

  record FMDomination(int sub, @Override @NotNull SourcePos sourcePos) implements ClausesProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(
        Doc.english("The"), Doc.ordinal(sub),
        Doc.english("clause is dominated by the other clauses, hence unreachable")
      );
    }

    @Override public @NotNull Severity level() {
      return Severity.WARN;
    }
  }
}
