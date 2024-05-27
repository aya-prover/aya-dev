// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.compile.JitCon;
import org.aya.syntax.core.def.ConDef;
import org.aya.syntax.core.def.ConDefLike;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.DataCall;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.reporter.Problem;
import org.aya.util.tyck.pat.PatClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface ClausesProblem extends Problem {
  @Override default @NotNull Severity level() { return Severity.ERROR; }

  private static @NotNull Doc termToHint(@Nullable Term term, @NotNull PrettierOptions options) {
    return term == null ? Doc.empty() : Doc.sep(Doc.english("confluence: this clause is substituted to"),
      Doc.code(term.toDoc(options)));
  }

  record Conditions(
    @Override @NotNull SourcePos sourcePos,
    @NotNull SourcePos iPos,
    int i,
    @NotNull ImmutableSeq<Term> args,
    @NotNull UnifyInfo info,
    @NotNull UnifyInfo.Comparison comparison
  ) implements ClausesProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      var begin = Doc.sep(
        Doc.plain("The"),
        Doc.ordinal(i),
        Doc.english("clause matches on a path constructor. We failed to unify")
      );
      var end = Doc.vcat(Doc.english("for the arguments:"),
        Doc.par(1, BasePrettier.coreArgsDoc(options, args.view())),
        Doc.english("Normalized:"),
        Doc.par(1, BasePrettier.coreArgsDoc(options, args.view().map(info::whnf))));
      return info.describeUnify(options, comparison, begin, end);
    }

    @Override public @NotNull SeqView<WithPos<Doc>> inlineHints(@NotNull PrettierOptions options) {
      var data = termToHint(comparison.actual(), options);
      return SeqView.of(new WithPos<>(iPos, data));
    }
  }

  /**
   * @param i          expected
   * @param j          actual
   * @param comparison expected = i, actual = j
   */
  record Confluence(
    @Override @NotNull SourcePos sourcePos,
    int i, int j,
    @NotNull UnifyInfo.Comparison comparison,
    @NotNull UnifyInfo info,
    @NotNull SourcePos iPos, @NotNull SourcePos jPos
  ) implements ClausesProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      var line = Doc.sep(
        Doc.plain("The"),
        Doc.ordinal(i),
        Doc.english("and the"),
        Doc.ordinal(j),
        Doc.english("clauses are not confluent because we failed to unify"));
      return info.describeUnify(options, comparison, line, Doc.plain("and"));
    }

    @Override public @NotNull SeqView<WithPos<Doc>> inlineHints(@NotNull PrettierOptions options) {
      return Seq.of(
        new WithPos<>(iPos, termToHint(comparison.expected(), options)),
        new WithPos<>(jPos, termToHint(comparison.actual(), options))).view();
    }
  }

  record MissingCase(
    @Override @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<PatClass<ImmutableSeq<Term>>> errs
  ) implements ClausesProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      var cases = Doc.vcat(errs.map(err ->
        BasePrettier.coreArgsDoc(options, err.term().view())));
      return Doc.vcat(Doc.english("Unhandled case:"),
        Doc.nest(2, cases));
    }
  }

  record UnsureCase(
    @Override @NotNull SourcePos sourcePos,
    @NotNull ConDefLike con,
    @NotNull DataCall dataCall
  ) implements ClausesProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.vcat(
        // Use `unsure` instead of `not sure`, which is used in Agda
        Doc.english("I'm unsure if there should be a case for constructor"),
        Doc.par(1, switch (con) {
          case JitCon jitCon -> BasePrettier.refVar(jitCon);
          case ConDef.Delegate conDef -> conDef.core().toDoc(options);
        }),
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

    @Override public @NotNull Severity level() { return Severity.WARN; }
  }

  record FMDomination(int sub, @Override @NotNull SourcePos sourcePos) implements ClausesProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(
        Doc.english("The"), Doc.ordinal(sub),
        Doc.english("clause is dominated by the other clauses, hence unreachable")
      );
    }

    @Override public @NotNull Severity level() { return Severity.WARN; }
  }
}
