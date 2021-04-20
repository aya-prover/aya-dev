// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.unify;

import org.aya.api.util.NormalizeMode;
import org.aya.core.def.Def;
import org.aya.core.term.*;
import org.aya.tyck.trace.Trace;
import org.aya.util.Ordering;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author re-xyr
 * @apiNote Use {@link UntypedDefEq#compare(Term, Term)} instead of visiting directly!
 */
public record UntypedDefEq(
  @NotNull PatDefEq defeq, @NotNull Ordering cmp
) implements Term.Visitor<@NotNull Term, @Nullable Term> {
  public @Nullable Term compare(@NotNull Term lhs, @NotNull Term rhs) {
    final var x = lhs.accept(this, rhs);
    return x != null ? x.normalize(NormalizeMode.WHNF) : null;
  }

  @Override public void traceEntrance(@NotNull Term lhs, @NotNull Term rhs) {
    defeq.defeq.traceEntrance(new Trace.UnifyT(lhs, rhs, defeq.defeq.pos));
  }

  @Override public void traceExit(@Nullable Term term) {
    defeq.traceExit(true);
  }

  @Override public @Nullable Term visitRef(@NotNull RefTerm lhs, @NotNull Term preRhs) {
    if (preRhs instanceof RefTerm rhs
      && defeq.defeq.varSubst.getOrDefault(rhs.var(), rhs.var()) == lhs.var()) {
      return defeq.defeq.localCtx.get(rhs.var());
    }
    return null;
  }

  @Override public @Nullable Term visitApp(@NotNull ElimTerm.App lhs, @NotNull Term preRhs) {
    if (!(preRhs.normalize(NormalizeMode.WHNF) instanceof ElimTerm.App rhs)) return null;
    var preFnType = compare(lhs.of(), rhs.of());
    if (!(preFnType instanceof FormTerm.Pi fnType)) return null;
    if (!defeq.compare(lhs.arg().term(), rhs.arg().term(), fnType.param().type())) return null;
    return fnType.substBody(lhs.arg().term());
  }

  @Override public @Nullable Term visitAccess(CallTerm.@NotNull Access lhs, @NotNull Term preRhs) {
    if (!(preRhs instanceof CallTerm.Access rhs)) return null;
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
    if (!(preRhs.normalize(NormalizeMode.WHNF) instanceof FormTerm.Pi rhs)) return null;
    return defeq.defeq.checkParam(lhs.param(), rhs.param(), FormTerm.Univ.OMEGA, () -> null, () -> {
      var bodyIsOk = defeq.compare(lhs.body(), rhs.body(), FormTerm.Univ.OMEGA);
      if (!bodyIsOk) return null;
      return FormTerm.Univ.OMEGA;
    });
  }

  @Override public @Nullable Term visitSigma(@NotNull FormTerm.Sigma lhs, @NotNull Term preRhs) {
    if (!(preRhs.normalize(NormalizeMode.WHNF) instanceof FormTerm.Sigma rhs)) return null;
    return defeq.defeq.checkParams(lhs.params(), rhs.params(), () -> null, () -> {
      var bodyIsOk = defeq.compare(lhs.params().last().type(), rhs.params().last().type(), FormTerm.Univ.OMEGA);
      if (!bodyIsOk) return null;
      return FormTerm.Univ.OMEGA;
    });
  }

  @Override public @Nullable Term visitUniv(@NotNull FormTerm.Univ lhs, @NotNull Term preRhs) {
    if (!(preRhs.normalize(NormalizeMode.WHNF) instanceof FormTerm.Univ rhs)) return null;
    defeq.defeq.tycker.equations.add(lhs.sort(), rhs.sort(), cmp, defeq.defeq.pos);
    return new FormTerm.Univ((cmp == Ordering.Lt ? lhs.sort() : rhs.sort()).succ(1));
  }

  private static Term unreachable() {
    throw new IllegalStateException();
  }

  @Override public @NotNull Term visitTup(@NotNull IntroTerm.Tuple lhs, @NotNull Term preRhs) {
    return unreachable();
  }

  @Override public @NotNull Term visitNew(@NotNull IntroTerm.New newTerm, @NotNull Term term) {
    return unreachable();
  }

  @Override public @NotNull Term visitFnCall(@NotNull CallTerm.Fn lhs, @NotNull Term preRhs) {
    return unreachable();
  }

  @Override public @Nullable Term visitDataCall(@NotNull CallTerm.Data lhs, @NotNull Term preRhs) {
    if (!(preRhs.normalize(NormalizeMode.WHNF) instanceof CallTerm.Data rhs) || lhs.ref() != rhs.ref()) return null;
    var subst = defeq.levels(lhs.ref(), lhs.sortArgs(), rhs.sortArgs());
    var args = defeq.defeq.visitArgs(lhs.args(), rhs.args(), Term.Param.subst(Def.defTele(lhs.ref()), subst));
    // Do not need to be computed precisely because unification won't need this info
    return args ? FormTerm.Univ.OMEGA : null;
  }

  @Override public @Nullable Term visitStructCall(@NotNull CallTerm.Struct lhs, @NotNull Term preRhs) {
    if (!(preRhs.normalize(NormalizeMode.WHNF) instanceof CallTerm.Struct rhs) || lhs.ref() != rhs.ref()) return null;
    var subst = defeq.levels(lhs.ref(), lhs.sortArgs(), rhs.sortArgs());
    var args = defeq.defeq.visitArgs(lhs.args(), rhs.args(), Term.Param.subst(Def.defTele(lhs.ref()), subst));
    return args ? FormTerm.Univ.OMEGA : null;
  }

  @Override public @NotNull Term visitPrimCall(CallTerm.@NotNull Prim prim, @NotNull Term term) {
    return unreachable();
  }

  @Override public @NotNull Term visitConCall(@NotNull CallTerm.Con conCall, @NotNull Term term) {
    return unreachable();
  }

  @Override public @NotNull Term visitLam(@NotNull IntroTerm.Lambda lhs, @NotNull Term preRhs) {
    return unreachable();
  }
}
