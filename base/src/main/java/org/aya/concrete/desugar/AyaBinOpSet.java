// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.desugar;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.Reporter;
import org.aya.concrete.desugar.error.OperatorProblem;
import org.aya.concrete.resolve.context.Context;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public final class AyaBinOpSet extends BinOpSet {
  public final @NotNull Reporter reporter;

  public AyaBinOpSet(@NotNull Reporter reporter) {
    this.reporter = reporter;
  }

  @Override protected void reportSelfBind(@NotNull SourcePos sourcePos) {
    reporter.report(new OperatorProblem.BindSelfError(sourcePos));
    throw new Context.ResolvingInterruptedException();
  }

  @Override protected void reportCyclic(ImmutableSeq<ImmutableSeq<BinOP>> cycles) {
    cycles.forEach(c -> reporter.report(new OperatorProblem.Circular(c)));
    throw new Context.ResolvingInterruptedException();
  }
}
