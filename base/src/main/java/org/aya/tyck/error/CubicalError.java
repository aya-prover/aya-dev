// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.concrete.Expr;
import org.aya.core.term.Term;
import org.aya.distill.BaseDistiller;
import org.aya.generic.ExprProblem;
import org.aya.guest0x0.cubical.Restr;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.TyckState;
import org.aya.tyck.unify.Unifier;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.NotNull;

public sealed interface CubicalError extends ExprProblem, TyckError {
  record BoundaryDisagree(
    @NotNull Expr expr,
    @NotNull Term lhs,
    @NotNull Term rhs,
    @Override @NotNull Unifier.FailureData failureData,
    @Override @NotNull TyckState state
  ) implements CubicalError, UnifyError {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return describeUnify(options, Doc.english("The boundary"), lhs,
        Doc.english("disagrees with"), rhs);
    }
  }

  record FaceMismatch(
    @NotNull Expr expr,
    @NotNull Restr<Term> face,
    @NotNull Restr<Term> cof
  ) implements CubicalError {
    @Override
    public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.vcat(Doc.english("The face(s) in the partial element:"),
        Doc.par(1, BaseDistiller.restr(options, face)),
        Doc.english("must cover the face(s) specified in type:"),
        Doc.par(1, BaseDistiller.restr(options, cof)));
    }
  }
}
