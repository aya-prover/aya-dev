// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.term;

import asia.kala.Unit;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.core.term.CoreTerm;
import org.mzi.api.util.NormalizeMode;
import org.mzi.core.visitor.NormalizeFixpoint;
import org.mzi.core.visitor.SubstFixpoint;
import org.mzi.tyck.sort.LevelSubst;
import org.mzi.util.Decision;

/**
 * @author ice1000
 */
public interface Term extends CoreTerm {
  <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);
  <P, Q, R> R accept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q);
  @Contract(pure = true) @NotNull Decision whnf();

  default @NotNull Term subst(@NotNull SubstFixpoint.TermSubst subst) {
    return subst(subst, LevelSubst.EMPTY);
  }

  default @NotNull Term subst(@NotNull SubstFixpoint.TermSubst subst, @NotNull LevelSubst levelSubst) {
    return accept(new SubstFixpoint(subst, levelSubst), Unit.INSTANCE);
  }

  @Override default @NotNull Term normalize(@NotNull NormalizeMode mode) {
    return accept(NormalizeFixpoint.INSTANCE, mode);
  }

  default @Nullable Term dropTelePi(int n) {
    if (n == 0) return this;
    var term = this;
    while (term instanceof DT.PiTerm dt) {
      var tele = dt.telescope();
      while (n > 0 && tele != null) {
        tele = tele.next();
        n--;
      }
      if (n == 0) return tele != null ? new DT.PiTerm(tele, dt.last(), dt.co()) : dt.last();
      term = dt.last().normalize(NormalizeMode.WHNF);
    }
    return null;
  }

  default @Nullable Term dropTeleLam(int n) {
    if (n == 0) return this;
    var term = this;
    while (term instanceof LamTerm lam) {
      var tele = lam.tele();
      while (n > 0 && tele != null) {
        tele = tele.next();
        n--;
      }
      if (n == 0) return tele != null ? new LamTerm(tele, lam.body()) : lam.body();
      term = lam.body().normalize(NormalizeMode.WHNF);
    }
    return null;
  }

  interface Visitor<P, R> {
    R visitRef(@NotNull RefTerm term, P p);
    R visitLam(@NotNull LamTerm term, P p);
    R visitPi(@NotNull DT.PiTerm term, P p);
    R visitSigma(@NotNull DT.SigmaTerm term, P p);
    R visitUniv(@NotNull UnivTerm term, P p);
    R visitApp(AppTerm.@NotNull Apply term, P p);
    R visitFnCall(AppTerm.@NotNull FnCall fnCall, P p);
    R visitTup(@NotNull TupTerm term, P p);
    R visitProj(@NotNull ProjTerm term, P p);
    R visitHole(@NotNull AppTerm.HoleApp term, P p);
  }

  interface BiVisitor<P, Q, R> {
    R visitRef(@NotNull RefTerm term, P p, Q q);
    R visitLam(@NotNull LamTerm term, P p, Q q);
    R visitPi(@NotNull DT.PiTerm term, P p, Q q);
    R visitSigma(@NotNull DT.SigmaTerm term, P p, Q q);
    R visitUniv(@NotNull UnivTerm term, P p, Q q);
    R visitApp(AppTerm.@NotNull Apply term, P p, Q q);
    R visitFnCall(AppTerm.@NotNull FnCall fnCall, P p, Q q);
    R visitTup(@NotNull TupTerm term, P p, Q q);
    R visitProj(@NotNull ProjTerm term, P p, Q q);
    R visitHole(@NotNull AppTerm.HoleApp term, P p, Q q);
  }
}
