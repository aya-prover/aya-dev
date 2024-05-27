// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.pretty.doc.Doc;
import org.aya.syntax.concrete.Expr;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;

public sealed interface CubicalError extends TyckError {
  record BoundaryDisagree(
    @Override @NotNull WithPos<Expr> expr,
    @NotNull UnifyInfo.Comparison comparison,
    @NotNull UnifyInfo info
  ) implements CubicalError, SourceNodeProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return info.describeUnify(options, comparison, Doc.english("The boundary"),
        Doc.english("disagrees with"));
    }
  }

/*
  record FaceMismatch(
    @NotNull Expr expr,
    @Override @NotNull SourcePos sourcePos,
    @NotNull Restr<Term> face,
    @NotNull Restr<Term> cof
  ) implements CubicalError {
    @Override
    public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.vcat(Doc.english("The face(s) in the partial element:"),
        Doc.par(1, BasePrettier.restr(options, face)),
        Doc.english("must cover the face(s) specified in type:"),
        Doc.par(1, BasePrettier.restr(options, cof)));
    }
  }

  record CoeVaryingType(
    @NotNull Expr expr,
    @NotNull Term type,
    @Nullable Term typeInst,
    @NotNull Restr<Term> restr,
    boolean stuck
  ) implements CubicalError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      var typeDoc = type.toDoc(options);
      var under = typeInst != null ? typeInst.toDoc(options) : null;
      var buf = MutableList.of(
        Doc.english("Under the cofibration:"),
        Doc.par(1, BasePrettier.restr(options, restr)));
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
*/

  record PathConDominateError(@NotNull SourcePos sourcePos) implements TyckError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.english("The path constructor must not be a constant path");
    }
  }
}
