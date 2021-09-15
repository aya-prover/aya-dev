// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.util;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.util.Arg;
import org.aya.core.term.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

public interface EtaConversion {

  /**
   * Note this is not a full eta-reduction. Several cases are ignored.
   * The overall goal is to determine whether a term can be reduced to RefTerm
   */
  static @NotNull Term simpleEtaReduction(@NotNull Term term) {
    return switch (term) {
      case IntroTerm.Lambda lambdaTerm -> etaLambda(lambdaTerm);
      case IntroTerm.Tuple  tupleTerm  -> etaTuple(tupleTerm);
      case ElimTerm.App     appTerm    -> etaApp(appTerm);
      case ElimTerm.Proj    projTerm   -> etaProj(projTerm);
      // Ignore other cases because they are useless in becoming a RefTerm
      default                          -> term;
    };
  }

  private static @NotNull Term etaLambda(@NotNull IntroTerm.Lambda lambdaTerm) {
    var etaBodyTerm = simpleEtaReduction(lambdaTerm.body());
    var lastTerm = getLastTerm(etaBodyTerm);
    var bodyWithoutLastTerm = constructBodyWithoutLast(etaBodyTerm, lastTerm);
    if (lastTerm instanceof RefTerm lastRefTerm
      && compareRefTerm(lambdaTerm.param().toTerm(), lastRefTerm)
      && appearFree(lastRefTerm, bodyWithoutLastTerm)) return simpleEtaReduction(bodyWithoutLastTerm);
    return IntroTerm.Lambda.make(ImmutableSeq.of(lambdaTerm.param()), etaBodyTerm);
  }

  private static @NotNull Term getLastTerm(@NotNull Term term) {
    return switch (term) {
      case IntroTerm.Lambda lamTerm -> getLastTerm(lamTerm.body());
      case ElimTerm.App     appTerm -> appTerm.arg().term();
      default                       -> term;
    };
  }

  private static @NotNull Term constructBodyWithoutLast(@NotNull Term term, @NotNull Term lastTerm) {
    return switch (term) {
      case IntroTerm.Lambda lamTerm -> IntroTerm.Lambda.make(ImmutableSeq.of(lamTerm.param()),
        constructBodyWithoutLast(lamTerm.body(), lastTerm));
      case ElimTerm.App     appTerm -> compareRefTerm(appTerm.arg().term(), lastTerm) ? appTerm.of() : appTerm;
      default                       -> term;
    };
  }

  private static boolean appearFree(@NotNull RefTerm refTerm, @NotNull Term term) {
    return switch (term) {
      case RefTerm            rTerm   -> !compareRefTerm(refTerm, rTerm);
      case IntroTerm.Lambda   lamTerm -> appearFree(refTerm, lamTerm.body());
      case ElimTerm.App       appTerm -> appearFree(refTerm, appTerm.arg().term())
        && appearFree(refTerm, appTerm.of());
      // TODO: There are many other cases, but if they all need to be considered, maybe a visitor is better
      default                         -> false;
    };
  }

  @VisibleForTesting
  static boolean compareRefTerm(@NotNull Term lhs, @NotNull Term rhs) {
    if (!(lhs instanceof RefTerm lhsRefTerm)
      || !(rhs instanceof RefTerm rhsRefTerm)) return false;
    return lhsRefTerm.var().name().equals(rhsRefTerm.var().name());
  }

  private static @NotNull Term etaTuple(@NotNull IntroTerm.Tuple tupleTerm) {
    var etaItems = tupleTerm.items().map(EtaConversion::simpleEtaReduction);
    if (etaItems.isEmpty()) return tupleTerm;
    var defaultRes = new IntroTerm.Tuple(etaItems);
    // Get first item's Proj.of Term to compare with other items'
    var firstItem = etaItems.first();
    FormTerm.Sigma targetSigmaTerm;
    if (firstItem instanceof ElimTerm.Proj projTerm
      && projTerm.of() instanceof RefTerm refTerm
      && refTerm.type() instanceof FormTerm.Sigma sigmaTerm) targetSigmaTerm = sigmaTerm;
    else return defaultRes;
    // Make sure targetSigma's size is equal to this tuple's size
    if (!targetSigmaTerm.params().sizeEquals(tupleTerm.items().size())) return defaultRes;
    // Make sure every Proj.of Term is the same and index match the position
    for (var i = 0; i < etaItems.size(); ++i) {
      var item = etaItems.get(i);
      if (!(item instanceof ElimTerm.Proj itemProjTerm)
        || !compareRefTerm(itemProjTerm.of(), refTerm)
        || (itemProjTerm.ix() != i + 1)) return defaultRes;
    }
    return refTerm;
  }

  private static @NotNull Term etaApp(@NotNull ElimTerm.App appTerm) {
    return new ElimTerm.App(appTerm.of(), new Arg<>(simpleEtaReduction(appTerm.arg().term()),
      appTerm.arg().explicit()));
  }

  private static @NotNull Term etaProj(@NotNull ElimTerm.Proj projTerm) {
    return new ElimTerm.Proj(simpleEtaReduction(projTerm.of()), projTerm.ix());
  }
}
