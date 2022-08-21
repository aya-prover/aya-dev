// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.concrete.Expr;
import org.aya.core.term.PrimTerm;
import org.aya.core.term.Term;
import org.aya.generic.ExprProblem;
import org.aya.guest0x0.cubical.Restr;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.unify.DefEq;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.NotNull;

public sealed interface CubicalProblem extends ExprProblem {
  default @Override @NotNull Severity level() {
    return Severity.ERROR;
  }

  record BoundaryDisagree(
    @NotNull Expr expr,
    @NotNull Term lhs,
    @NotNull Term rhs,
    @NotNull DefEq.FailureData failure
  ) implements CubicalProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.vcat(Doc.english("The boundary"),
        Doc.par(1, lhs.toDoc(options)),
        Doc.english("disagrees with"),
        Doc.par(1, rhs.toDoc(options)),
        Doc.english("In particular, we failed to unify"),
        Doc.par(1, failure.lhs().toDoc(options)),
        Doc.english("with"),
        Doc.par(1, failure.rhs().toDoc(options)));
    }
  }

  record FaceMismatch(
    @NotNull Expr expr,
    @NotNull Restr<Term> face,
    @NotNull PrimTerm.Cof cof
  ) implements CubicalProblem {
    @Override
    public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.vcat(Doc.english("The face(s) in the partial element:"),
        Doc.par(1, face.toDoc()),
        Doc.english("must cover the face(s) specified in type:"),
        Doc.par(1, cof.toDoc(options)));
    }
  }
}
