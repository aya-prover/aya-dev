// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Sample;
import org.aya.core.def.Tycked;
import org.aya.tyck.error.CounterexampleError;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SampleTycker implements Sample.Visitor<StmtTycker, @Nullable Tycked> {
  public static final @NotNull SampleTycker INSTANCE = new SampleTycker();

  private SampleTycker() {
  }

  @Override public @Nullable Tycked visitExample(Sample.@NotNull Working example, @NotNull StmtTycker stmtTycker) {
    if (example.delegate() instanceof Decl decl)
      return new Tycked.Example(decl.accept(stmtTycker, stmtTycker.newTycker()));
    else return null;
  }

  @Contract("_, _ -> new") @Override
  public @NotNull Tycked visitCounterexample(Sample.@NotNull Counter example, StmtTycker stmtTycker) {
    var delegate = example.delegate();
    var def = delegate.accept(stmtTycker, new ExprTycker(example.reporter(), stmtTycker.traceBuilder()));
    var problems = example.reporter().problems().toImmutableSeq();
    if (problems.isEmpty()) {
      stmtTycker.reporter().report(new CounterexampleError(delegate.sourcePos(), delegate.ref()));
    }
    return new Tycked.Counterexample(def, problems);
  }
}
