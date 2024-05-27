// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.MetaCall;
import org.aya.syntax.ref.LocalVar;
import org.aya.tyck.TyckState;
import org.aya.tyck.tycker.Stateful;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

public sealed interface HoleProblem extends Problem {
  @NotNull MetaCall term();
  @Override default @NotNull Severity level() { return Severity.ERROR; }
  @Override default @NotNull SourcePos sourcePos() { return term().ref().pos(); }

  record BadSpineError(@Override @NotNull MetaCall term) implements HoleProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.vcat(
        Doc.english("The following spine is not in pattern fragment:"),
        BasePrettier.coreArgsDoc(options, term.args().view())
      );
    }
  }

  record IllTypedError(
    @Override @NotNull MetaCall term,
    @Override @NotNull TyckState state,
    @Override @NotNull Term solution
  ) implements HoleProblem, Stateful {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      var list = MutableList.of(Doc.english("The meta (denoted ? below) is supposed to satisfy:"),
        Doc.par(1, term.ref().req().toDoc(options)),
        Doc.english("However, the solution below does not seem so:"));
      UnifyInfo.exprInfo(solution, options, this, list);
      return Doc.vcat(list);
    }
  }

  record BadlyScopedError(
    @Override @NotNull MetaCall term,
    @NotNull Term solved,
    @NotNull Seq<LocalVar> allowed
  ) implements HoleProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.vcat(
        Doc.english("The solution"),
        Doc.par(1, solved.toDoc(options)),
        Doc.plain("is not well-scoped"),
        Doc.sep(Doc.english("Only the variables below are allowed:"),
          Doc.commaList(allowed.view()
            .map(BasePrettier::varDoc)
            .map(Doc::code))));
    }
  }

  record RecursionError(@Override @NotNull MetaCall term, @NotNull Term sol) implements HoleProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.vcat(
        Doc.sep(
          Doc.english("Trying to solve hole"),
          Doc.code(BasePrettier.linkDef(term.ref())),
          Doc.plain("as")),
        Doc.par(1, sol.toDoc(options)),
        Doc.english("which is recursive"));
    }
  }

  record CannotFindGeneralSolution(
    @NotNull ImmutableSeq<TyckState.Eqn> eqns
  ) implements Problem {
    @Override public @NotNull SourcePos sourcePos() { return eqns.getLast().pos(); }

    @Override public @NotNull SeqView<WithPos<Doc>> inlineHints(@NotNull PrettierOptions options) {
      return eqns.view().map(eqn -> new WithPos<>(eqn.pos(), eqn.toDoc(options)));
    }

    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.english("Solving equation(s) with not very general solution(s)");
    }

    @Override public @NotNull Severity level() { return Severity.INFO; }
  }
}
