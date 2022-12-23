// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.covariance;

import kala.collection.mutable.MutableHashSet;
import org.aya.core.term.*;
import org.aya.core.visitor.TermConsumer;
import org.aya.ref.AnyVar;
import org.aya.tyck.tycker.TyckState;
import org.jetbrains.annotations.NotNull;

public final class ParametersCovarianceChecker extends CovarianceChecker {
  private final @NotNull MutableHashSet<@NotNull AnyVar> vars;

  public ParametersCovarianceChecker(@NotNull TyckState state, @NotNull MutableHashSet<@NotNull AnyVar> vars) {
    super(state);
    this.vars = vars;
  }

  @Override
  protected boolean checkNonCovariant(@NotNull Term term) {
    new TermConsumer() {
      @Override public void pre(@NotNull Term term1) {
        if (term1 instanceof RefTerm refTerm) vars.remove(refTerm.var());
        if (term1 instanceof RefTerm.Field fieldRef) vars.remove(fieldRef.ref());
      }
    }.accept(term);
    return vars.isEmpty();
  }

  @Override
  protected boolean checkOtherwise(@NotNull Term term) {
    while (true) {
      switch (term) {
        case AppTerm app -> {
          if (checkNonCovariant(app.arg().term())) {
            return true;
          }
          term = app.of();
        }
        case PAppTerm pApp -> {
          for (var arg : pApp.args()) {
            if (checkNonCovariant(arg.term())) {
              return true;
            }
          }
          term = pApp.of();
        }
        case ProjTerm proj -> term = proj.of();
        case FieldTerm access -> term = access.of();
        case RefTerm refTerm -> {
          return false;
        }
        case default -> {
          return checkNonCovariant(term);
        }
      }
    }
  }
}
