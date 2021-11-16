// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import org.aya.api.error.BufferReporter;
import org.aya.core.def.Def;
import org.aya.core.def.UserDef;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.StmtTycker;
import org.aya.tyck.error.CounterexampleError;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface Sample extends Stmt {
  @NotNull Stmt delegate();

  /** @return <code>null</code> if the delegate is a command (not a definition) */
  @Nullable Def tyck(@NotNull StmtTycker stmtTycker);
  void tyckHeader(@NotNull StmtTycker stmtTycker);

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

    @Override public void tyckHeader(@NotNull StmtTycker stmtTycker) {
      if (delegate instanceof Decl decl) stmtTycker.tyckHeader(decl, stmtTycker.newTycker());
    }

    @Override public @Nullable Def tyck(@NotNull StmtTycker stmtTycker) {
      return delegate instanceof Decl decl ? stmtTycker.tyck(decl, stmtTycker.newTycker()) : null;
    }
  }

  record Counter(@NotNull Decl delegate, @NotNull BufferReporter reporter) implements Sample {
    public Counter(@NotNull Decl delegate) {
      this(delegate, new BufferReporter());
    }

    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitCounterexample(this, p);
    }

    @Override public void tyckHeader(@NotNull StmtTycker stmtTycker) {
      var exprTycker = new ExprTycker(this.reporter, stmtTycker.traceBuilder());
      stmtTycker.tyckHeader(delegate, exprTycker);
    }

    @Override public @NotNull Def tyck(@NotNull StmtTycker stmtTycker) {
      var exprTycker = new ExprTycker(this.reporter, stmtTycker.traceBuilder());
      var def = stmtTycker.tyck(delegate, exprTycker);
      var problems = this.reporter.problems().toImmutableArray();
      if (problems.isEmpty()) {
        stmtTycker.reporter().report(new CounterexampleError(delegate.sourcePos(), delegate.ref()));
      }
      if (def instanceof UserDef userDef) userDef.problems = problems;
      return def;
    }
  }
}
