// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.desugar;

import kala.collection.SeqView;
import org.aya.concrete.desugar.error.OperatorProblem;
import org.aya.util.binop.Assoc;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public final class AyaBinOpParser extends BinOpParser<AyaBinOpSet> {
  public AyaBinOpParser(@NotNull AyaBinOpSet opSet, @NotNull SeqView<@NotNull Elem> seq) {
    super(opSet, seq);
  }

  @Override protected @NotNull BinOpParser<AyaBinOpSet> replicate(@NotNull SeqView<@NotNull Elem> seq) {
    return new AyaBinOpParser(opSet, seq);
  }

  @Override protected void reportAmbiguousPred(String op1, String op2, SourcePos pos) {
    opSet.reporter.report(new OperatorProblem.AmbiguousPredError(op1, op2, pos));
  }

  @Override
  protected void reportFixityError(Assoc topAssoc, Assoc currentAssoc, String op2, String op1, SourcePos pos) {
    opSet.reporter.report(new OperatorProblem.FixityError(op1, currentAssoc, op2, topAssoc, pos));
  }
}
