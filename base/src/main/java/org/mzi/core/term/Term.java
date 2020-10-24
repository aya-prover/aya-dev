// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.term;

import asia.kala.Unit;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.core.term.CoreTerm;
import org.mzi.api.util.NormalizeMode;
import org.mzi.core.subst.LevelSubst;
import org.mzi.core.subst.TermSubst;
import org.mzi.core.visitor.NormalizeFixpoint;
import org.mzi.core.visitor.SubstFixpoint;
import org.mzi.util.Decision;

/**
 * @author ice1000
 */
public interface Term extends CoreTerm {
  <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);
  <P, Q, R> R accept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q);
  @Contract(pure = true) @NotNull Decision whnf();

  default @NotNull Term subst(@NotNull TermSubst subst) {
    return subst(subst, LevelSubst.EMPTY);
  }

  default @NotNull Term subst(@NotNull TermSubst subst, @NotNull LevelSubst levelSubst) {
    return accept(new SubstFixpoint(subst, levelSubst), Unit.INSTANCE);
  }

  @Override default @NotNull Term normalize(@NotNull NormalizeMode mode) {
    return accept(NormalizeFixpoint.INSTANCE, mode);
  }

  interface Visitor<P, R> {
    R visitRef(@NotNull RefTerm term, P p);
    R visitLam(@NotNull LamTerm term, P p);
    R visitDT(@NotNull DT term, P p);
    R visitUniv(@NotNull UnivTerm term, P p);
    R visitApp(AppTerm.@NotNull Apply term, P p);
    R visitFnCall(AppTerm.@NotNull FnCall fnCall, P p);
    R visitTup(@NotNull TupTerm term, P p);
    R visitProj(@NotNull ProjTerm term, P p);
    R visitHole(@NotNull HoleTerm term, P p);
  }

  interface BiVisitor<P, Q, R> {
    R visitRef(@NotNull RefTerm term, P p, Q q);
    R visitLam(@NotNull LamTerm term, P p, Q q);
    R visitDT(@NotNull DT term, P p, Q q);
    R visitUniv(@NotNull UnivTerm term, P p, Q q);
    R visitApp(AppTerm.@NotNull Apply term, P p, Q q);
    R visitFnCall(AppTerm.@NotNull FnCall fnCall, P p, Q q);
    R visitTup(@NotNull TupTerm term, P p, Q q);
    R visitProj(@NotNull ProjTerm term, P p, Q q);
    R visitHole(@NotNull HoleTerm term, P p, Q q);
  }
}
