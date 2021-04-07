// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.unify;

import org.aya.api.util.NormalizeMode;
import org.aya.core.def.Def;
import org.aya.core.term.*;
import org.aya.tyck.trace.Trace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author re-xyr
 * @apiNote Use {@link UntypedDefEq#compare(Term, Term)} instead of visiting directly!
 */
public record UntypedDefEq(
  @NotNull TypedDefEq defeq
) implements Term.Visitor<@NotNull Term, @Nullable Term> {
  public @Nullable Term compare(@NotNull Term lhs, @NotNull Term rhs) {
    final var x = lhs.accept(this, rhs);
    return x != null ? x.normalize(NormalizeMode.WHNF) : null;
  }

  @Override public void traceEntrance(@NotNull Term lhs, @NotNull Term rhs) {
    defeq.traceEntrance(new Trace.UnifyT(lhs, rhs, defeq.pos));
  }

  @Override public void traceExit(@Nullable Term term) {
    defeq.traceExit(true);
  }

  @Override public @Nullable Term visitRef(@NotNull RefTerm lhs, @NotNull Term preRhs) {
    if (preRhs instanceof RefTerm rhs
      && defeq.varSubst.getOrDefault(rhs.var(), rhs.var()) == lhs.var()) {
      return defeq.localCtx.get(rhs.var());
    }
    return null;
  }

  @Override public @Nullable Term visitApp(@NotNull ElimTerm.App lhs, @NotNull Term preRhs) {
    if (!(preRhs instanceof ElimTerm.App rhs)) return null;
    var preFnType = compare(lhs.of(), rhs.of());
    if (!(preFnType instanceof FormTerm.Pi fnType)) return null;
    if (!defeq.compare(lhs.arg().term(), rhs.arg().term(), fnType.param().type())) return null;
    return fnType.substBody(lhs.arg().term());
  }

  @Override public @Nullable Term visitAccess(ElimTerm.@NotNull Access lhs, @NotNull Term preRhs) {
    if (!(preRhs instanceof ElimTerm.Access rhs)) return null;
    var preStructType = compare(lhs.of(), rhs.of());
    if (!(preStructType instanceof CallTerm.Struct structType)) return null;
    if (lhs.ref() != rhs.ref()) return null;
    return Def.defResult(lhs.ref());
  }

  @Override public @Nullable Term visitProj(@NotNull ElimTerm.Proj lhs, @NotNull Term preRhs) {
    if (!(preRhs instanceof ElimTerm.Proj rhs)) return null;
    var preTupType = compare(lhs.of(), rhs.of());
    if (!(preTupType instanceof FormTerm.Sigma tupType)) return null;
    if (lhs.ix() != rhs.ix()) return null;
    var params = tupType.params();
    for (int i = 1; i < lhs.ix(); i++) {
      var l = new ElimTerm.Proj(lhs, i);
      var currentParam = params.first();
      params = params.drop(1).map(x -> x.subst(currentParam.ref(), l));
    }
    if (params.isNotEmpty()) return params.first().type();
    return params.last().type();
  }

  @Override public @Nullable Term visitHole(CallTerm.@NotNull Hole lhs, @NotNull Term preRhs) {
    throw new IllegalStateException("No visitHole in UntypedDefEq");
  }

  @Override public @Nullable Term visitPi(@NotNull FormTerm.Pi lhs, @NotNull Term preRhs) {
    if (!(preRhs instanceof FormTerm.Pi rhs)) return null;
    return defeq.checkParam(lhs.param(), rhs.param(), () -> null, () -> {
      var bodyIsOk = defeq.compare(lhs.body(), rhs.body(), FormTerm.Univ.OMEGA);
      if (!bodyIsOk) return null;
      return FormTerm.Univ.OMEGA;
    });
  }

  @Override public @Nullable Term visitSigma(@NotNull FormTerm.Sigma lhs, @NotNull Term preRhs) {
    if (!(preRhs instanceof FormTerm.Sigma rhs)) return null;
    return defeq.checkParams(lhs.params(), rhs.params(), () -> null, () -> {
      var bodyIsOk = defeq.compare(lhs.params().last().type(), rhs.params().last().type(), FormTerm.Univ.OMEGA);
      if (!bodyIsOk) return null;
      return FormTerm.Univ.OMEGA;
    });
  }

  @Override public @Nullable Term visitUniv(@NotNull FormTerm.Univ lhs, @NotNull Term preRhs) {
    if (!(preRhs instanceof FormTerm.Univ)) return null;
    return FormTerm.Univ.OMEGA;
  }

  @Override public @Nullable Term visitTup(@NotNull IntroTerm.Tuple lhs, @NotNull Term preRhs) {
    throw new IllegalStateException("No visitTup in UntypedDefEq");
  }

  @Override public @Nullable Term visitNew(@NotNull IntroTerm.New newTerm, @NotNull Term term) {
    throw new IllegalStateException("No visitStruct in UntypedDefEq");
  }

  @Override public @Nullable Term visitFnCall(@NotNull CallTerm.Fn lhs, @NotNull Term preRhs) {
    throw new IllegalStateException("No visitFn in UntypedDefEq");
  }

  @Override public @Nullable Term visitDataCall(@NotNull CallTerm.Data lhs, @NotNull Term preRhs) {
    throw new IllegalStateException("No visitData in UntypedDefEq");
  }

  @Override public @Nullable Term visitStructCall(@NotNull CallTerm.Struct lhs, @NotNull Term preRhs) {
    throw new IllegalStateException("No visitStruct in UntypedDefEq");
  }

  @Override public @Nullable Term visitPrimCall(CallTerm.@NotNull Prim prim, @NotNull Term term) {
    throw new IllegalStateException("No visitPrim in UntypedDefEq");
  }

  @Override public @Nullable Term visitConCall(@NotNull CallTerm.Con conCall, @NotNull Term term) {
    throw new IllegalStateException("No visitCon in UntypedDefEq");
  }

  @Override public @Nullable Term visitLam(@NotNull IntroTerm.Lambda lhs, @NotNull Term preRhs) {
    throw new IllegalStateException("No visitLam in UntypedDefEq");
  }

  public UntypedDefEq(@NotNull TypedDefEq defeq) {
    this.defeq = defeq;
  }
}
