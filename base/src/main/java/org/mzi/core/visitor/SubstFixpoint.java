package org.mzi.core.visitor;

import asia.kala.EmptyTuple;
import org.jetbrains.annotations.NotNull;
import org.mzi.core.subst.TermSubst;
import org.mzi.core.term.RefTerm;
import org.mzi.core.term.Term;

/**
 * @author ice1000
 */
public record SubstFixpoint(@NotNull TermSubst subst) implements TermFixpoint<EmptyTuple> {
  @Override
  public @NotNull Term visitRef(@NotNull RefTerm term, EmptyTuple unused) {
    return subst.get(term.ref(), term);
  }
}
