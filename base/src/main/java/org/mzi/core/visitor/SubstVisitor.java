package org.mzi.core.visitor;

import org.jetbrains.annotations.NotNull;
import org.mzi.core.subst.TermSubst;
import org.mzi.core.term.RefTerm;
import org.mzi.core.term.Term;

/**
 * @author ice1000
 */
public record SubstVisitor(@NotNull TermSubst subst) implements BaseTermVisitor<Void> {
  @Override
  public @NotNull Term visitRef(@NotNull RefTerm term, Void unused) {
    return subst.get(term.ref(), term);
  }
}
