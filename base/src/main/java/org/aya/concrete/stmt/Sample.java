// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import org.aya.api.error.CollectingReporter;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.core.def.Def;
import org.aya.core.def.UserDef;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.StmtTycker;
import org.aya.tyck.error.CounterexampleError;
import org.aya.tyck.trace.Trace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface Sample extends Stmt {
  @NotNull Stmt delegate();

  /** @return <code>null</code> if the delegate is a command (not a definition) */
  @Nullable Def tyck(@NotNull Reporter reporter, Trace.@Nullable Builder traceBuilder, boolean headerOnly);

  @Override default @NotNull SourcePos sourcePos() {
    return delegate().sourcePos();
  }

  @Override default @NotNull Accessibility accessibility() {
    return Accessibility.Private;
  }

  record Working(@NotNull Stmt delegate) implements Sample {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitExample(this, p);
    }

    @Override public @Nullable Def tyck(
      @NotNull Reporter reporter,
      Trace.@Nullable Builder traceBuilder,
      boolean headerOnly
    ) {
      return delegate instanceof Decl decl ?
        new StmtTycker(reporter, traceBuilder, headerOnly).tyck(decl) : null;
    }
  }

  record Counter(@NotNull Decl delegate, @NotNull CollectingReporter reporter) implements Sample {
    public Counter(@NotNull Decl delegate) {
      this(delegate, new CollectingReporter());
    }

    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitCounterexample(this, p);
    }

    @Override public @Nullable Def tyck(
      @NotNull Reporter reporter,
      Trace.@Nullable Builder traceBuilder,
      boolean headerOnly
    ) {
      var stmtTycker = new StmtTycker(reporter, traceBuilder, headerOnly);
      var def = stmtTycker.tyck(delegate, new ExprTycker(this.reporter, stmtTycker.traceBuilder()));
      var problems = this.reporter.problems().toImmutableSeq();
      if (problems.isEmpty()) {
        stmtTycker.reporter().report(new CounterexampleError(delegate.sourcePos(), delegate.ref()));
      }
      if (def instanceof UserDef userDef) {
        userDef.problems = problems;
        return userDef;
      } else {
        // TODO[ice]: a counterexample should be a function, a data, or a struct, not other stuffs!
        return null;
      }
    }
  }
}
