// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import org.aya.concrete.Sample;
import org.aya.core.def.Tycked;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public final class SampleTycker implements Sample.Visitor<StmtTycker, Tycked> {
  public static final @NotNull SampleTycker INSTANCE = new SampleTycker();

  private SampleTycker() {
  }

  @Contract("_, _ -> new") @Override
  public @NotNull Tycked visitExample(Sample.@NotNull Working example, @NotNull StmtTycker stmtTycker) {
    return new Tycked.Example(example.delegate.accept(stmtTycker, stmtTycker.newTycker()));
  }

  @Override public Tycked visitCounterexample(Sample.@NotNull Counter example, StmtTycker stmtTycker) {
    var def = example.delegate.accept(stmtTycker, new ExprTycker(example.reporter, stmtTycker.traceBuilder()));
    return new Tycked.Counterexample(def, example.reporter.problems().toImmutableSeq());
  }
}
