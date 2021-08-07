// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.stmt;

import org.aya.api.error.CollectingReporter;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.core.def.Def;
import org.aya.tyck.SampleTycker;
import org.aya.tyck.StmtTycker;
import org.aya.tyck.trace.Trace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface Sample extends Stmt {
  @NotNull Stmt delegate();

  /** @return <code>null</code> if the delegate is a command (not a definition) */
  default @Nullable Def tyck(@NotNull Reporter reporter, Trace.@Nullable Builder traceBuilder) {
    return doAccept(SampleTycker.INSTANCE, new StmtTycker(reporter, traceBuilder));
  }

  interface Visitor<P, R> {
    R visitExample(@NotNull Working example, P p);
    R visitCounterexample(@NotNull Counter example, P p);
  }

  @Override default @NotNull SourcePos sourcePos() {
    return delegate().sourcePos();
  }

  @Override default @NotNull Accessibility accessibility() {
    return Accessibility.Private;
  }

  <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p);

  @Override default <P, R> R doAccept(Stmt.@NotNull Visitor<P, R> visitor, P p) {
    return doAccept((Visitor<? super P, ? extends R>) visitor, p);
  }

  record Working(@NotNull Stmt delegate) implements Sample {
    @Override public <P, R> R doAccept(Sample.@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitExample(this, p);
    }
  }

  record Counter(@NotNull Decl delegate, @NotNull CollectingReporter reporter) implements Sample {
    public Counter(@NotNull Decl delegate) {
      this(delegate, new CollectingReporter());
    }

    @Override public <P, R> R doAccept(Sample.@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitCounterexample(this, p);
    }
  }
}
