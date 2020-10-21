package org.mzi.core.visitor;

import asia.kala.EmptyTuple;
import org.jetbrains.annotations.NotNull;
import org.mzi.core.sort.Sort;
import org.mzi.core.subst.LevelSubst;
import org.mzi.core.subst.TermSubst;
import org.mzi.core.term.RefTerm;
import org.mzi.core.term.Term;

/**
 * This doesn't substitute references underlying function calls.
 * @author ice1000
 */
public class SubstFixpoint implements TermFixpoint<EmptyTuple> {
  private final @NotNull TermSubst termSubst;
  private final @NotNull LevelSubst levelSubst;

  public SubstFixpoint(@NotNull TermSubst termSubst, @NotNull LevelSubst levelSubst) {
    this.termSubst = termSubst;
    this.levelSubst = levelSubst;
  }

  @Override
  public @NotNull Sort visitSort(@NotNull Sort sort, EmptyTuple unused) {
    return sort.substSort(levelSubst);
  }

  @Override
  public @NotNull Term visitRef(@NotNull RefTerm term, EmptyTuple unused) {
    return termSubst.get(term.ref(), term);
  }
}
