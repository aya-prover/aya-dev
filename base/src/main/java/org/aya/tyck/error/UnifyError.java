// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import kala.collection.mutable.MutableList;
import org.aya.concrete.Expr;
import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.term.Term;
import org.aya.generic.ExprProblem;
import org.aya.generic.util.NormalizeMode;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.TyckState;
import org.aya.tyck.unify.Unifier;
import org.aya.util.pretty.PrettierOptions;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public interface UnifyError extends TyckError {
  @NotNull TyckState state();
  @NotNull Unifier.FailureData failureData();

  default @NotNull Doc describeUnify(
    @NotNull PrettierOptions options,
    @NotNull Doc prologue,
    @NotNull Term actual,
    @NotNull Doc epilogue,
    @NotNull Term expected
  ) {
    var actualDoc = actual.toDoc(options);
    var expectedDoc = expected.toDoc(options);
    var actualNFDoc = nf(actual).toDoc(options);
    var expectedNFDoc = nf(expected).toDoc(options);
    var buf = MutableList.of(prologue);
    compareExprs(epilogue, actualDoc, expectedDoc, actualNFDoc, expectedNFDoc, buf);
    var failureTermL = failureData().lhs();
    var failureTermR = failureData().rhs();
    var failureLhs = failureTermL.toDoc(options);
    if (!failureLhs.equals(actualDoc)
      && !failureLhs.equals(expectedDoc)
      && !failureLhs.equals(actualNFDoc)
      && !failureLhs.equals(expectedNFDoc)
    ) {
      buf.append(Doc.english("In particular, we failed to unify"));
      compareExprs(Doc.plain("with"),
        failureLhs, failureTermR.toDoc(options),
        nf(failureTermL).toDoc(options),
        nf(failureTermR).toDoc(options),
        buf);
    }
    return Doc.vcat(buf);
  }
  @NotNull private Term nf(Term failureTermL) {
    return failureTermL.normalize(state(), NormalizeMode.NF);
  }
  private static void compareExprs(@NotNull Doc mid, Doc actualDoc, Doc expectedDoc, Doc actualNFDoc, Doc expectedNFDoc, MutableList<@NotNull Doc> buf) {
    buf.append(Doc.par(1, actualDoc));
    if (!actualNFDoc.equals(actualDoc))
      buf.append(Doc.par(1, Doc.parened(Doc.sep(Doc.plain("Normalized:"), actualNFDoc))));
    buf.append(mid);
    buf.append(Doc.par(1, expectedDoc));
    if (!expectedNFDoc.equals(expectedDoc))
      buf.append(Doc.par(1, Doc.parened(Doc.sep(Doc.plain("Normalized:"), expectedNFDoc))));
  }

  record Type(
    @Override @NotNull Expr expr,
    @NotNull Term expected,
    @NotNull Term actual,
    @Override @NotNull Unifier.FailureData failureData,
    @Override @NotNull TyckState state
  ) implements ExprProblem, UnifyError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      var prologue = Doc.vcat(
        Doc.english("Cannot check the expression"),
        Doc.par(1, expr.toDoc(options)),
        Doc.english("of type"));
      return describeUnify(options, prologue, actual, Doc.english("against the type"), expected);
    }
  }

  record ConReturn(
    @NotNull TeleDecl.DataCtor ctor,
    @NotNull Term expected,
    @NotNull Term actual,
    @Override @NotNull Unifier.FailureData failureData,
    @Override @NotNull TyckState state
  ) implements UnifyError {
    @Override public @NotNull SourcePos sourcePos() {
      return ctor.sourcePos;
    }

    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      var prologue = Doc.vcat(
        Doc.english("Cannot make sense of the return type of the constructor"),
        Doc.par(1, ctor.toDoc(options)),
        Doc.english("which eventually returns"));
      return describeUnify(options, prologue, actual, Doc.english("while it should return"), expected);
    }
  }
}
