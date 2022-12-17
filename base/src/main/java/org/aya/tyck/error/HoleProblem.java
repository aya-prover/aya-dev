// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.core.term.MetaTerm;
import org.aya.core.term.Term;
import org.aya.pretty.BasePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.ref.LocalVar;
import org.aya.tyck.TyckState;
import org.aya.util.pretty.PrettierOptions;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

/** @author ice1000 */
public sealed interface HoleProblem extends Problem {
  @NotNull MetaTerm term();

  @Override default @NotNull Severity level() {
    return Severity.ERROR;
  }

  /** @author ice1000 */
  record BadSpineError(
    @Override @NotNull MetaTerm term,
    @Override @NotNull SourcePos sourcePos
  ) implements HoleProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.vcat(
        Doc.english("Can't perform pattern unification on hole with the following spine:"),
        BasePrettier.argsDoc(options, term.args())
      );
    }
  }

  record BadlyScopedError(
    @Override @NotNull MetaTerm term,
    @NotNull Term solved,
    @NotNull Seq<LocalVar> scopeCheck,
    @Override @NotNull SourcePos sourcePos
  ) implements HoleProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.vcat(
        Doc.english("The solution"),
        Doc.par(1, solved.toDoc(options)),
        Doc.plain("is not well-scoped"),
        Doc.cat(Doc.english("In particular, these variables are not in scope:"),
          Doc.ONE_WS,
          Doc.commaList(scopeCheck.view()
            .map(BasePrettier::varDoc)
            .map(Doc::code))));
    }
  }

  /**
   * @author ice1000
   */
  record RecursionError(
    @Override @NotNull MetaTerm term,
    @NotNull Term sol,
    @Override @NotNull SourcePos sourcePos
  ) implements HoleProblem {
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
    @Override public @NotNull SourcePos sourcePos() {
      return eqns.last().pos();
    }

    @Override public @NotNull SeqView<WithPos<Doc>> inlineHints(@NotNull PrettierOptions options) {
      return eqns.view().map(eqn -> new WithPos<>(eqn.pos(), eqn.toDoc(options)));
    }

    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.english("Solving equation(s) with not very general solution(s)");
    }

    @Override public @NotNull Severity level() {
      return Severity.INFO;
    }
  }
}
