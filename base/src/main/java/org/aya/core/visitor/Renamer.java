// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import kala.collection.mutable.MutableMap;
import kala.tuple.Unit;
import org.aya.core.term.FormTerm;
import org.aya.core.term.IntroTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;

/**
 * IntelliJ Renamer, LOL
 *
 * @author ice1000
 */
public final class Renamer implements TermFixpoint<Unit> {
  private final Substituter.TermSubst subst = new Substituter.TermSubst(MutableMap.create());

  @Override public @NotNull Term visitFieldRef(@NotNull RefTerm.Field field, Unit unit) {
    return subst.map().getOrDefault(field.ref(), field);
  }

  @Override public @NotNull Term visitRef(@NotNull RefTerm ref, Unit unused) {
    return subst.map().getOrElse(ref.var(), () ->
      TermFixpoint.super.visitRef(ref, Unit.unit()));
  }

  @Override public @NotNull Term visitLam(IntroTerm.@NotNull Lambda lambda, Unit unit) {
    var param = handleBinder(lambda.param());
    return new IntroTerm.Lambda(param, lambda.body().accept(this, unit));
  }

  @Override public @NotNull Term visitPi(FormTerm.@NotNull Pi pi, Unit unit) {
    var param = handleBinder(pi.param());
    return new FormTerm.Pi(param, pi.body().accept(this, unit));
  }

  private @NotNull Term.Param handleBinder(@NotNull Term.Param param) {
    var v = param.renameVar();
    var type = param.type().accept(this, Unit.unit());
    subst.addDirectly(param.ref(), new RefTerm(v, type));
    return new Term.Param(v, type, param.explicit());
  }

  @Override public @NotNull Term visitSigma(FormTerm.@NotNull Sigma term, Unit unit) {
    return new FormTerm.Sigma(term.params().map(this::handleBinder));
  }
}
