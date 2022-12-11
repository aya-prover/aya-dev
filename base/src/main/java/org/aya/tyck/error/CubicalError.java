// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import kala.collection.mutable.MutableList;
import org.aya.concrete.Expr;
import org.aya.core.term.Term;
import org.aya.distill.BaseDistiller;
import org.aya.generic.ExprProblem;
import org.aya.guest0x0.cubical.Restr;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.TyckState;
import org.aya.tyck.unify.Unifier;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  record CoeVaryingType(
    @NotNull Expr expr,
    @NotNull Term type,
    @Nullable Term typeInst,
    @NotNull Restr<Term> restr,
    boolean stuck
  ) implements CubicalError {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      var typeDoc = type.toDoc(options);
      var under = typeInst != null ? typeInst.toDoc(options) : null;
      var buf = MutableList.of(
        Doc.english("Under the cofibration:"),
        Doc.par(1, BaseDistiller.restr(options, restr)));
      if (stuck)
        buf.append(Doc.english("I am not sure if the type is constant, as my normalization is blocked for type:"));
      else buf.append(Doc.english("The type in the body still depends on the interval parameter:"));
      buf.append(Doc.par(1, typeDoc));
      if (under != null && !typeDoc.equals(under)) buf.append(
        Doc.par(1, Doc.parened(Doc.sep(Doc.plain("Normalized under cofibration:"), under))));
      buf.append(Doc.english("which is not allowed in coercion"));
      return Doc.vcat(buf);
    }
  }

  record PathConDominateError(@NotNull SourcePos sourcePos) implements TyckError {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.english("The path constructor must not be a constant path");
    }
  }
}
