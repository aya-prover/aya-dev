// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.value.visitor;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.tuple.Unit;
import org.aya.api.ref.LocalVar;
import org.aya.core.term.FormTerm;
import org.aya.core.term.IntroTerm;
import org.aya.core.term.Term;
import org.aya.value.FormValue;
import org.aya.value.IntroValue;
import org.aya.value.RefValue;
import org.aya.value.Value;
import org.jetbrains.annotations.NotNull;

public class Quoter implements Visitor<Unit, Term> {
  private Term.Param quote(Value.Param param, Unit u) {
    return new Term.Param(new LocalVar(param.ref().name()), param.type().accept(this, u), param.explicit());
  }

  @Override
  public Term visitPi(FormValue.@NotNull Pi pi, Unit u) {
    var param = quote(pi.param(), u);
    var body = pi.func().apply(new RefValue.Neu(param.ref())).accept(this, u);
    return new FormTerm.Pi(param, body);
  }

  @Override
  public Term visitData(FormValue.@NotNull Data data, Unit unit) {
    return null;
  }

  @Override
  public Term visitStruct(FormValue.@NotNull Struct struct, Unit unit) {
    return null;
  }

  @Override
  public Term visitSigma(FormValue.@NotNull Sigma sigma, Unit u) {
    var param = quote(sigma.param(), u);
    var body = sigma.func().apply(new RefValue.Neu(param.ref())).accept(this, u);
    var params = DynamicSeq.of(param);
    if (body instanceof FormTerm.Sigma sig) {
      params.appendAll(sig.params());
    } else {
      params.append(new Term.Param(new LocalVar("_"), body, true));
    }
    return new FormTerm.Sigma(params.toImmutableSeq());
  }

  @Override
  public Term visitUnit(FormValue.@NotNull Unit unit, Unit u) {
    return new FormTerm.Sigma(ImmutableSeq.empty());
  }

  @Override
  public Term visitUniv(FormValue.@NotNull Univ univ, Unit u) {
    return new FormTerm.Univ(univ.sort());
  }

  @Override
  public Term visitLam(IntroValue.@NotNull Lam lam, Unit u) {
    var param = quote(lam.param(), u);
    var body = lam.func().apply(new RefValue.Neu(param.ref())).accept(this, u);
    return new IntroTerm.Lambda(param, body);
  }

  @Override
  public Term visitCtor(IntroValue.@NotNull Ctor ctor, Unit unit) {
    return null;
  }

  @Override
  public Term visitNew(IntroValue.@NotNull New newVal, Unit unit) {
    return null;
  }

  @Override
  public Term visitPair(IntroValue.@NotNull Pair pair, Unit u) {
    var left = pair.left().accept(this, u);
    var items = DynamicSeq.of(left);
    var right = pair.right().accept(this, u);
    if (right instanceof IntroTerm.Tuple tup) {
      items.appendAll(tup.items());
    } else {
      items.append(right);
    }
    return new IntroTerm.Tuple(items.toImmutableSeq());
  }

  @Override
  public Term visitTT(IntroValue.@NotNull TT tt, Unit unit) {
    return new IntroTerm.Tuple(ImmutableSeq.empty());
  }

  @Override
  public Term visitNeu(RefValue.@NotNull Neu neu, Unit u) {
    return null;
  }

  @Override
  public Term visitFlex(RefValue.@NotNull Flex flex, Unit u) {
    return null;
  }
}
