// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import kala.collection.Seq;
import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.WithPos;
import org.aya.core.term.CallTerm;
import org.aya.core.term.Term;
import org.aya.distill.BaseDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.tyck.unify.EqnSet;
import org.jetbrains.annotations.NotNull;

/** @author ice1000 */
public sealed interface HoleProblem extends Problem {
  @NotNull CallTerm.Hole term();

  @Override default @NotNull Severity level() {
    return Severity.ERROR;
  }

  /** @author ice1000 */
  record BadSpineError(
    @NotNull CallTerm.Hole term,
    @NotNull SourcePos sourcePos
  ) implements HoleProblem {
    @Override public @NotNull Doc describe() {
      return Doc.vcat(
        Doc.english("Can't perform pattern unification on hole with the following spine:"),
        Doc.commaList(term.args().map(a -> a.toDoc(DistillerOptions.DEFAULT)))
      );
    }
  }

  record BadlyScopedError(
    @NotNull CallTerm.Hole term,
    @NotNull Term solved,
    @NotNull Seq<LocalVar> scopeCheck,
    @NotNull SourcePos sourcePos
  ) implements HoleProblem {
    @Override public @NotNull Doc describe() {
      return Doc.vcat(
        Doc.english("The solution"),
        Doc.indent(1, solved.toDoc(DistillerOptions.DEFAULT)),
        Doc.plain("is not well-scoped"),
        Doc.cat(Doc.english("In particular, these variables are not in scope:"),
          Doc.ONE_WS,
          Doc.commaList(scopeCheck.view()
            .map(BaseDistiller::varDoc)
            .map(doc -> Doc.styled(Style.code(), doc)))));
    }
  }

  /**
   * @author ice1000
   */
  record RecursionError(
    @NotNull CallTerm.Hole term,
    @NotNull Term sol,
    @NotNull SourcePos sourcePos
  ) implements HoleProblem {
    @Override public @NotNull Doc describe() {
      return Doc.vcat(
        Doc.sep(
          Doc.english("Trying to solve hole"),
          Doc.styled(Style.code(), BaseDistiller.linkDef(term.ref())),
          Doc.plain("as")),
        Doc.indent(1, sol.toDoc(DistillerOptions.DEFAULT)),
        Doc.english("which is recursive"));
    }
  }

  record CannotFindGeneralSolution(
    @NotNull ImmutableSeq<EqnSet.Eqn> eqns
  ) implements Problem {
    @Override public @NotNull SourcePos sourcePos() {
      return eqns.last().pos();
    }

    @Override public @NotNull SeqLike<WithPos<Doc>> inlineHints() {
      return eqns.map(eqn -> new WithPos<>(eqn.pos(), eqn.toDoc(DistillerOptions.DEFAULT)));
    }

    @Override public @NotNull Doc describe() {
      return Doc.english("Solving equation(s) with not very general solution(s)");
    }

    @Override public @NotNull Severity level() {
      return Severity.WARN;
    }
  }
}
