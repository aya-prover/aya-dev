package org.mzi.core.term;

import asia.kala.EmptyTuple;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.core.term.CoreTerm;
import org.mzi.api.util.NormalizeMode;
import org.mzi.core.subst.TermSubst;
import org.mzi.core.visitor.NormalizeVisitor;
import org.mzi.core.visitor.SubstVisitor;
import org.mzi.util.Decision;

/**
 * @author ice1000
 */
public interface Term extends CoreTerm {
  <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);
  @Contract(pure = true) @NotNull Decision whnf();

  default @NotNull Term subst(@NotNull TermSubst subst) {
    return accept(new SubstVisitor(subst), EmptyTuple.INSTANCE);
  }

  default @NotNull Term normalize(@NotNull NormalizeMode mode) {
    return accept(NormalizeVisitor.INSTANCE, mode);
  }

  interface Visitor<P, R> {
    R visitRef(@NotNull RefTerm term, P p);
    R visitLam(@NotNull LamTerm term, P p);
    R visitPi(@NotNull DT term, P p);
    R visitUniv(@NotNull UnivTerm term, P p);
    R visitApp(@NotNull AppTerm.Apply term, P p);
  }
}
