// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
import kala.tuple.Unit;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.core.term.CallTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public interface VarConsumer<P> extends TermConsumer<P> {
  @Override default Unit visitRef(@NotNull RefTerm term, P p) {
    visitVar(term.var(), p);
    return Unit.unit();
  }

  @Override default Unit visitHole(@NotNull CallTerm.Hole term, P p) {
    visitVar(term.ref(), p);
    return TermConsumer.super.visitHole(term, p);

  }

  @Override default Unit visitFnCall(CallTerm.@NotNull Fn fnCall, P p) {
    visitVar(fnCall.ref(), p);
    return TermConsumer.super.visitFnCall(fnCall, p);
  }

  @Override default Unit visitPrimCall(@NotNull CallTerm.Prim prim, P p) {
    visitVar(prim.ref(), p);
    return TermConsumer.super.visitPrimCall(prim, p);
  }

  @Override default Unit visitDataCall(@NotNull CallTerm.Data dataCall, P p) {
    visitVar(dataCall.ref(), p);
    return TermConsumer.super.visitDataCall(dataCall, p);
  }

  @Override default Unit visitConCall(@NotNull CallTerm.Con conCall, P p) {
    visitVar(conCall.ref(), p);
    return TermConsumer.super.visitConCall(conCall, p);
  }

  @Override default Unit visitStructCall(@NotNull CallTerm.Struct structCall, P p) {
    visitVar(structCall.ref(), p);
    return TermConsumer.super.visitStructCall(structCall, p);
  }

  @Contract(mutates = "this,param2") void visitVar(Var usage, P p);

  /**
   * @author ice1000
   * @see Term#findUsages(Var)
   */
  final class UsageCounter implements VarConsumer<Unit> {
    public final @NotNull Var var;
    private int usageCount = 0;

    @Contract(pure = true) public UsageCounter(@NotNull Var var) {
      this.var = var;
    }

    @Contract(pure = true) public int usageCount() {
      return usageCount;
    }

    @Contract(mutates = "this") @Override public void visitVar(Var usage, Unit unit) {
      if (var == usage) usageCount++;
    }
  }

  final class ScopeChecker implements VarConsumer<Unit> {
    public final @NotNull ImmutableSeq<LocalVar> allowed;
    public final @NotNull Buffer<LocalVar> invalidVars = Buffer.create();

    @Contract(pure = true) public ScopeChecker(@NotNull ImmutableSeq<LocalVar> allowed) {
      this.allowed = allowed;
    }

    @Contract(mutates = "this") @Override public void visitVar(Var v, Unit unit) {
      if (v instanceof LocalVar local && !allowed.contains(local)) invalidVars.append(local);
    }
  }
}
