// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.tyck.unify;

import org.glavo.kala.control.Option;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.util.NormalizeMode;
import org.mzi.core.term.*;

/**
 * @author re-xyr
 */
public record UntypedDefEq(
  @NotNull TypedDefEq defeq
) implements Term.Visitor<@NotNull Term, @Nullable Term> {
  public @Nullable
  Term compare(@NotNull Term lhs, @NotNull Term rhs) {
    // [xyr]: If we delete the type parameter it'll produce a worse warning.
    return Option.<Term>of(lhs.accept(this, rhs)).map(x -> x.normalize(NormalizeMode.WHNF)).getOrNull();
  }

  @Override
  public @Nullable
  Term visitRef(@NotNull RefTerm lhs, @NotNull Term preRhs) {
    if (preRhs instanceof RefTerm rhs
      && defeq.varSubst.getOrDefault(rhs.var(), rhs.var()) == lhs.var()) {
      return defeq.localCtx.get(rhs.var());
    }
    return null;
  }

  @Override
  public @Nullable
  Term visitApp(@NotNull AppTerm.Apply lhs, @NotNull Term preRhs) {
    if (!(preRhs instanceof AppTerm.Apply rhs)) return null;
    var preFnType = compare(lhs.fn(), rhs.fn());
    if (!(preFnType instanceof PiTerm fnType)) return null;
    if (!defeq.compare(lhs.arg().term(), rhs.arg().term(), fnType.param().type())) return null;
    return fnType.body().subst(fnType.param().ref(), lhs.arg().term());
  }

  @Override
  public @Nullable
  Term visitProj(@NotNull ProjTerm lhs, @NotNull Term preRhs) {
    if (!(preRhs instanceof ProjTerm rhs)) return null;
    var preTupType = compare(lhs.tup(), rhs.tup());
    if (!(preTupType instanceof SigmaTerm tupType)) return null;
    if (lhs.ix() != rhs.ix()) return null;
    var params = tupType.params();
    var body = tupType.body();
    for (int i = 1; i < lhs.ix(); i++) {
      var l = new ProjTerm(lhs, i);
      var currentParam = params.first();
      params = params.drop(1).map(x -> x.subst(currentParam.ref(), l));
      body = body.subst(currentParam.ref(), l);
    }
    if (!params.isEmpty()) return params.first().type();
    return body;
  }

  @Override
  public @Nullable
  Term visitHole(AppTerm.@NotNull HoleApp lhs, @NotNull Term preRhs) {
    throw new IllegalStateException("No visitHole in UntypedDefEq");
  }

  @Override
  public @Nullable
  Term visitPi(@NotNull PiTerm lhs, @NotNull Term preRhs) {
    if (!(preRhs instanceof PiTerm rhs)) return null;
    if (!defeq.checkParam(lhs.param(), rhs.param())) return null;
    var bodyIsOk = defeq.compare(lhs.body(), rhs.body(), UnivTerm.OMEGA);
    defeq.localCtx.remove(lhs.param().ref());
    if (!bodyIsOk) return null;
    return UnivTerm.OMEGA;
  }

  @Override
  public @Nullable
  Term visitSigma(@NotNull SigmaTerm lhs, @NotNull Term preRhs) {
    if (!(preRhs instanceof SigmaTerm rhs)) return null;
    if (!defeq.checkParams(lhs.params(), rhs.params())) return null;
    var bodyIsOk = defeq.compare(lhs.body(), rhs.body(), UnivTerm.OMEGA);
    lhs.params().forEach(param -> defeq.localCtx.remove(param.ref()));
    if (!bodyIsOk) return null;
    return UnivTerm.OMEGA;
  }

  @Override
  public @Nullable
  Term visitUniv(@NotNull UnivTerm lhs, @NotNull Term preRhs) {
    if (!(preRhs instanceof UnivTerm rhs)) return null;
    return UnivTerm.OMEGA;
  }

  @Override
  public @Nullable
  Term visitTup(@NotNull TupTerm lhs, @NotNull Term preRhs) {
    throw new IllegalStateException("No visitTup in UntypedDefEq");
  }

  @Override
  public @Nullable
  Term visitFnCall(@NotNull AppTerm.FnCall lhs, @NotNull Term preRhs) {
    throw new IllegalStateException("No visitFn in UntypedDefEq");
  }

  @Override
  public @Nullable
  Term visitDataCall(@NotNull AppTerm.DataCall lhs, @NotNull Term preRhs) {
    throw new IllegalStateException("No visitData in UntypedDefEq");
  }

  @Override
  public @Nullable
  Term visitLam(@NotNull LamTerm lhs, @NotNull Term preRhs) {
    throw new IllegalStateException("No visitLam in UntypedDefEq");
  }

  public UntypedDefEq(@NotNull TypedDefEq defeq) {
    this.defeq = defeq;
  }
}
