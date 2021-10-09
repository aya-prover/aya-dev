// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.ops;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.util.Arg;
import org.aya.core.term.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

public interface Eta {
  /**
   * Note this is not a full eta-reduction. Several cases are ignored.
   * The overall goal is to determine whether a term can be reduced to RefTerm
   */
  static @NotNull Term uneta(@NotNull Term term) {
    return switch (term) {
      case IntroTerm.Lambda lambdaTerm -> {
        var etaBodyTerm = uneta(lambdaTerm.body());
        var lastTerm = getLastTerm(etaBodyTerm);
        var bodyWithoutLastTerm = constructBodyWithoutLast(etaBodyTerm, lastTerm);
        if (lastTerm instanceof RefTerm lastRefTerm
          && compareRefTerm(lambdaTerm.param().toTerm(), lastRefTerm)
          && appearFree(lastRefTerm, bodyWithoutLastTerm)) yield uneta(bodyWithoutLastTerm);
        yield IntroTerm.Lambda.make(ImmutableSeq.of(lambdaTerm.param()), etaBodyTerm);
      }
      case IntroTerm.Tuple tupleTerm -> {
        if (tupleTerm.items().isEmpty()) yield tupleTerm;
        var etaItems = tupleTerm.items().map(Eta::uneta);
        var defaultRes = new IntroTerm.Tuple(etaItems);
        // Get first item's Proj.of Term to compare with other items'
        var firstItem = etaItems.first();
        if (!(firstItem instanceof ElimTerm.Proj projTerm
          && projTerm.of() instanceof RefTerm refTerm
          && refTerm.type() instanceof FormTerm.Sigma sigmaTerm)) yield defaultRes;
        // Make sure targetSigma's size is equal to this tuple's size
        if (!sigmaTerm.params().sizeEquals(tupleTerm.items().size())) yield defaultRes;
        // Make sure every Proj.of Term is the same and index match the position
        for (var i = 0; i < etaItems.size(); ++i) {
          var item = etaItems.get(i);
          if (!(item instanceof ElimTerm.Proj itemProjTerm)
            || !compareRefTerm(itemProjTerm.of(), refTerm)
            || (itemProjTerm.ix() != i + 1)) yield defaultRes;
        }
        yield refTerm;
      }
      case ElimTerm.App appTerm -> new ElimTerm.App(appTerm.of(),
        new Arg<>(uneta(appTerm.arg().term()), appTerm.arg().explicit()));
      case ElimTerm.Proj projTerm -> new ElimTerm.Proj(uneta(projTerm.of()), projTerm.ix());
      // Ignore other cases because they are useless in becoming a RefTerm
      default -> term;
    };
  }

  private static @NotNull Term getLastTerm(@NotNull Term term) {
    return switch (term) {
      case IntroTerm.Lambda lamTerm -> getLastTerm(lamTerm.body());
      case ElimTerm.App appTerm -> appTerm.arg().term();
      default -> term;
    };
  }

  private static @NotNull Term constructBodyWithoutLast(@NotNull Term term, @NotNull Term lastTerm) {
    return switch (term) {
      case IntroTerm.Lambda lamTerm -> IntroTerm.Lambda.make(ImmutableSeq.of(lamTerm.param()),
        constructBodyWithoutLast(lamTerm.body(), lastTerm));
      case ElimTerm.App appTerm -> compareRefTerm(appTerm.arg().term(), lastTerm) ? appTerm.of() : appTerm;
      default -> term;
    };
  }

  private static boolean appearFree(@NotNull RefTerm refTerm, @NotNull Term term) {
    //noinspection ConstantConditions
    return switch (term) {
      case RefTerm rTerm -> !compareRefTerm(refTerm, rTerm);
      case IntroTerm.Lambda lamTerm -> appearFree(refTerm, lamTerm.body());
      case ElimTerm.App appTerm -> appearFree(refTerm, appTerm.arg().term())
        && appearFree(refTerm, appTerm.of());
      // TODO: There are many other cases, but if they all need to be considered, maybe a visitor is better
      default -> false;
    };
  }

  @VisibleForTesting
  static boolean compareRefTerm(@NotNull Term lhs, @NotNull Term rhs) {
    if (!(lhs instanceof RefTerm lhsRefTerm
      && rhs instanceof RefTerm rhsRefTerm)) return false;
    return lhsRefTerm.var().name().equals(rhsRefTerm.var().name());
  }
}
