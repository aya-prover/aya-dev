// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.Map;
import kala.collection.mutable.DynamicSeq;
import kala.tuple.Unit;
import org.aya.core.term.FormTerm;
import org.aya.core.term.IntroTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.ref.Var;
import org.jetbrains.annotations.NotNull;

/**
 * This doesn't substitute references underlying function calls.
 *
 * @author ice1000
 */
public final class Substituter implements TermFixpoint<Unit> {
  private final @NotNull DynamicSeq<Var> boundVars = DynamicSeq.create();
  private final @NotNull Map<Var, ? extends Term> termSubst;
  private final int ulift;

  public Substituter(@NotNull Map<Var, ? extends Term> termSubst, int ulift) {
    this.termSubst = termSubst;
    this.ulift = ulift;
  }

  public Substituter(@NotNull Subst subst, int ulift) {
    this(subst.map(), ulift);
  }

  @Override public @NotNull Term visitFieldRef(@NotNull RefTerm.Field term, Unit unit) {
    return termSubst.getOption(term.ref())
      .map(t -> t.rename().lift(boundVars.contains(term.ref()) ? 0 : ulift))
      .getOrDefault(term);
  }

  @Override public @NotNull Term visitRef(@NotNull RefTerm term, Unit unused) {
    return termSubst.getOption(term.var())
      .map(t -> t.rename().lift(boundVars.contains(term.var()) ? 0 : ulift))
      .getOrElse(() -> TermFixpoint.super.visitRef(term, Unit.unit()));
  }

  @Override public @NotNull Term visitPi(FormTerm.@NotNull Pi term, Unit unit) {
    boundVars.append(term.param().ref());
    return TermFixpoint.super.visitPi(term, unit);
  }

  @Override public @NotNull Term visitSigma(FormTerm.@NotNull Sigma term, Unit unit) {
    term.params().forEach(param -> boundVars.append(param.ref()));
    return TermFixpoint.super.visitSigma(term, unit);
  }

  @Override public @NotNull Term visitLam(IntroTerm.@NotNull Lambda term, Unit unit) {
    boundVars.append(term.param().ref());
    return TermFixpoint.super.visitLam(term, unit);
  }

  @Override public int ulift() {
    return ulift;
  }

}
