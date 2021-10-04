// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import kala.collection.Map;
import kala.collection.mutable.MutableMap;
import kala.tuple.Unit;
import org.aya.api.ref.Var;
import org.aya.core.term.FormTerm;
import org.aya.core.term.IntroTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;

/** IntelliJ Renamer, LOL */
public record Renamer(@NotNull Substituter.TermSubst subst) implements TermSubstituter {
  public Renamer() {
    this(new Substituter.TermSubst(MutableMap.create()));
  }

  @Override public @NotNull Term visitLam(IntroTerm.@NotNull Lambda lambda, Unit unit) {
    var param = lambda.param();
    var renamed = param.rename();
    subst.addDirectly(param.ref(), renamed.toTerm());
    return new IntroTerm.Lambda(renamed, lambda.body().accept(this, unit));
  }

  @Override public @NotNull Term visitPi(FormTerm.@NotNull Pi pi, Unit unit) {
    var param = pi.param();
    var renamed = param.rename();
    subst.addDirectly(param.ref(), renamed.toTerm());
    return new FormTerm.Pi(renamed, pi.body().accept(this, unit));
  }

  @Override public @NotNull Term visitSigma(FormTerm.@NotNull Sigma term, Unit unit) {
    var renamedParams = term.params().map(param -> {
      var renamedVar = param.renameVar();
      var type = param.type().accept(this, Unit.unit());
      subst.addDirectly(param.ref(), new RefTerm(renamedVar, type));
      return new Term.Param(renamedVar, type, param.explicit());
    });
    return new FormTerm.Sigma(renamedParams);
  }

  @Override public Map<Var, Term> termSubst() {
    return subst.map();
  }
}
