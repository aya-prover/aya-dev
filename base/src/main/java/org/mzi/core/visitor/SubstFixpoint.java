package org.mzi.core.visitor;

import asia.kala.EmptyTuple;
import org.jetbrains.annotations.NotNull;
import org.mzi.core.subst.TermSubst;
import org.mzi.core.term.RefTerm;
import org.mzi.core.term.Term;

/**
 * This doesn't substitute references underlying function calls.
 * @author ice1000
 */
public class SubstFixpoint implements TermFixpoint<EmptyTuple> {
  private final @NotNull TermSubst subst;

  public SubstFixpoint(@NotNull TermSubst subst) {
    this.subst = subst;
  }

  @Override
  public @NotNull Term visitRef(@NotNull RefTerm term, EmptyTuple unused) {
    return subst.get(term.ref(), term);
  }
}
