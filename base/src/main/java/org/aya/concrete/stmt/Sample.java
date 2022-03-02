// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.def.Def;
import org.aya.core.def.UserDef;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.StmtTycker;
import org.aya.tyck.error.CounterexampleError;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.BufferReporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface Sample extends Stmt {
  @NotNull Stmt delegate();

  @Override default boolean needTyck(@NotNull ImmutableSeq<String> currentMod) {
    return delegate().needTyck(currentMod);
  }

  /** @return <code>null</code> if the delegate is a command (not a definition) */
  @Nullable Def tyck(@NotNull StmtTycker stmtTycker, @NotNull ExprTycker exprTycker);
  void tyckHeader(@NotNull StmtTycker stmtTycker, @NotNull ExprTycker exprTycker);

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

    @Override public void tyckHeader(@NotNull StmtTycker stmtTycker, @NotNull ExprTycker exprTycker) {
      if (delegate instanceof Decl decl) stmtTycker.tyckHeader(decl, exprTycker);
    }

    @Override public @Nullable Def tyck(@NotNull StmtTycker stmtTycker, @NotNull ExprTycker exprTycker) {
      return delegate instanceof Decl decl ? stmtTycker.tyck(decl, exprTycker) : null;
    }
  }

  record Counter(@NotNull Decl delegate, @NotNull BufferReporter reporter) implements Sample {
    public Counter(@NotNull Decl delegate) {
      this(delegate, new BufferReporter());
    }

    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitCounterexample(this, p);
    }

    @Override public void tyckHeader(@NotNull StmtTycker stmtTycker, @NotNull ExprTycker exprTycker) {
      stmtTycker.tyckHeader(delegate, exprTycker);
    }

    @Override public @NotNull Def tyck(@NotNull StmtTycker stmtTycker, @NotNull ExprTycker exprTycker) {
      var def = stmtTycker.tyck(delegate, exprTycker);
      var problems = reporter.problems().toImmutableSeq();
      if (problems.isEmpty()) {
        stmtTycker.reporter().report(new CounterexampleError(delegate.sourcePos(), delegate.ref()));
      }
      if (def instanceof UserDef userDef) userDef.problems = problems;
      return def;
    }
  }
}
