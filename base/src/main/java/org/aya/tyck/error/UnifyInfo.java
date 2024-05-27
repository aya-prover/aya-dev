// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import kala.collection.mutable.MutableList;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.core.term.Term;
import org.aya.tyck.TyckState;
import org.aya.tyck.tycker.Stateful;
import org.aya.unify.TermComparator;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record UnifyInfo(@Override @NotNull TyckState state) implements Stateful {
  private static void compareExprs(@NotNull Doc mid, Doc actualDoc, Doc expectedDoc, Doc actualNFDoc, Doc expectedNFDoc, MutableList<@NotNull Doc> buf) {
    exprInfo(actualDoc, actualNFDoc, buf);
    buf.append(mid);
    exprInfo(expectedDoc, expectedNFDoc, buf);
  }

  public static void exprInfo(Doc actualDoc, Doc actualNFDoc, MutableList<@NotNull Doc> buf) {
    buf.append(Doc.par(1, actualDoc));
    if (!actualNFDoc.equals(actualDoc))
      buf.append(Doc.par(1, Doc.parened(Doc.sep(Doc.plain("Normalized:"), actualNFDoc))));
  }

  public static void exprInfo(Term term, PrettierOptions options, Stateful state, MutableList<@NotNull Doc> buf) {
    exprInfo(term.toDoc(options), state.whnf(term).toDoc(options), buf);
  }

  public record Comparison(
    @NotNull Term actual,
    @NotNull Term expected,
    @NotNull TermComparator.FailureData failureData
  ) { }

  public @NotNull Doc describeUnify(
    @NotNull PrettierOptions options,
    @NotNull Comparison comparison,
    @NotNull Doc prologue,
    @NotNull Doc epilogue
  ) {
    var actualDoc = comparison.actual.toDoc(options);
    var expectedDoc = comparison.expected.toDoc(options);
    var actualNFDoc = whnf(comparison.actual).toDoc(options);
    var expectedNFDoc = whnf(comparison.expected).toDoc(options);
    var buf = MutableList.of(prologue);
    compareExprs(epilogue, actualDoc, expectedDoc, actualNFDoc, expectedNFDoc, buf);
    var failureTermL = comparison.failureData.lhs();
    var failureTermR = comparison.failureData.rhs();
    var failureLhs = failureTermL.toDoc(options);
    if (!Objects.equals(failureLhs, actualDoc)
      && !Objects.equals(failureLhs, expectedDoc)
      && !Objects.equals(failureLhs, actualNFDoc)
      && !Objects.equals(failureLhs, expectedNFDoc)
    ) {
      buf.append(Doc.english("In particular, we failed to unify"));
      compareExprs(Doc.plain("with"),
        failureLhs, failureTermR.toDoc(options),
        whnf(failureTermL).toDoc(options),
        whnf(failureTermR).toDoc(options),
        buf);
    }
    return Doc.vcat(buf);
  }
}
