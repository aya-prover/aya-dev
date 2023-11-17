// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.ops;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.term.*;
import org.aya.tyck.env.LocalCtx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

public record Eta(@NotNull LocalCtx ctx) {
  /**
   * Note this is not a full eta-reduction. Several cases are ignored.
   * The overall goal is to determine whether a term can be reduced to RefTerm
   */
  public @NotNull Term uneta(@NotNull Term term) {
    return switch (term) {
      case LamTerm(var param, var body) -> {
        var etaBody = uneta(body);
        var last = getLastTerm(etaBody);
        var bodyWoLast = constructBodyWithoutLast(etaBody, last);
        if (last instanceof RefTerm lastRef
          && compareRefTerm(param.toTerm(), lastRef)
          && bodyWoLast.findUsages(lastRef.var()) <= 0) yield uneta(bodyWoLast);
        yield LamTerm.make(ImmutableSeq.of(param), etaBody);
      }
      case TupTerm(var items) -> {
        if (items.isEmpty()) yield term;
        var etaItems = items.map(x -> x.descent(this::uneta));
        var defaultRes = new TupTerm(etaItems);
        // Get first item's Proj.of Term to compare with other items'
        var firstItem = etaItems.getFirst().term();
        if (!(firstItem instanceof ProjTerm proj
          && proj.of() instanceof RefTerm ref
          && ctx.get(ref.var()) instanceof SigmaTerm sigmaTerm)) yield defaultRes;
        // Make sure targetSigma's size is equal to this tuple's size
        if (!sigmaTerm.params().sizeEquals(items)) yield defaultRes;
        // Make sure every Proj.of Term is the same and index match the position
        for (var i = 0; i < etaItems.size(); ++i) {
          var item = etaItems.get(i).term();
          if (!(item instanceof ProjTerm itemProj)
            || !compareRefTerm(itemProj.of(), ref)
            || (itemProj.ix() != i + 1)) yield defaultRes;
        }
        yield ref;
      }
      case AppTerm(var f, var arg) -> new AppTerm(f, arg.descent(this::uneta));
      case ProjTerm(var t, var ix) -> new ProjTerm(uneta(t), ix);
      // Ignore other cases because they are useless in becoming a RefTerm
      default -> term;
    };
  }

  private static @NotNull Term getLastTerm(@NotNull Term term) {
    return switch (term) {
      case LamTerm lam -> getLastTerm(lam.body());
      case AppTerm app -> app.arg().term();
      default -> term;
    };
  }

  private static @NotNull Term constructBodyWithoutLast(@NotNull Term term, @NotNull Term lastTerm) {
    return switch (term) {
      case LamTerm lamTerm -> LamTerm.make(ImmutableSeq.of(lamTerm.param()),
        constructBodyWithoutLast(lamTerm.body(), lastTerm));
      case AppTerm appTerm -> compareRefTerm(appTerm.arg().term(), lastTerm) ? appTerm.of() : appTerm;
      default -> term;
    };
  }

  @VisibleForTesting
  public static boolean compareRefTerm(@NotNull Term lhs, @NotNull Term rhs) {
    if (!(lhs instanceof RefTerm lhsRef && rhs instanceof RefTerm rhsRefTerm)) return false;
    return lhsRef.var() == rhsRefTerm.var();
  }
}
