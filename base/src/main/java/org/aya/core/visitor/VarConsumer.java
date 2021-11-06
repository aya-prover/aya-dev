// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.tuple.Unit;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.core.term.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * @author ice1000
 */
public interface VarConsumer<P> extends TermConsumer<P> {
  @Override default Unit visitRef(@NotNull RefTerm term, P p) {
    visitVar(term.var(), p);
    return Unit.unit();
  }

  @Override default Unit visitFieldRef(@NotNull RefTerm.Field term, P p) {
    visitVar(term.ref(), p);
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
    public final @NotNull DynamicSeq<LocalVar> invalidVars = DynamicSeq.create();
    private final @NotNull DynamicSeq<LocalVar> bound = DynamicSeq.create();

    @Contract(pure = true) public ScopeChecker(@NotNull ImmutableSeq<LocalVar> allowed) {
      this.allowed = allowed;
    }

    @TestOnly @VisibleForTesting public boolean isCleared() {
      return bound.isEmpty();
    }

    @Override public Unit visitLam(IntroTerm.@NotNull Lambda term, Unit unit) {
      bound.append(term.param().ref());
      VarConsumer.super.visitLam(term, unit);
      bound.removeAt(bound.size() - 1);
      return unit;
    }

    @Override public Unit visitPi(FormTerm.@NotNull Pi term, Unit unit) {
      bound.append(term.param().ref());
      VarConsumer.super.visitPi(term, unit);
      bound.removeAt(bound.size() - 1);
      return unit;
    }

    @Override public Unit visitSigma(FormTerm.@NotNull Sigma term, Unit unit) {
      var start = bound.size();
      term.params().forEach(param -> {
        bound.append(param.ref());
        param.type().accept(this, Unit.unit());
      });
      for (int i = 0; i < term.params().size(); i++) {
        bound.removeAt(start); // TODO: DynamicSeq::removeAt(int, int)
      }
      return unit;
    }

    @Contract(mutates = "this") @Override public void visitVar(Var v, Unit unit) {
      if (v instanceof LocalVar local && !(allowed.contains(local) || bound.contains(local)))
        invalidVars.append(local);
    }
  }
}
