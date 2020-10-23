package org.mzi.tyck.unify;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.core.term.Term;
import org.mzi.util.Ordering;

/**
 * @author ice1000
 */
public abstract class DefEq implements Term.BiVisitor<@NotNull Term, @Nullable Term, @NotNull Boolean> {
  protected @NotNull Ordering ord;

  @Contract(pure = true) protected DefEq(@NotNull Ordering ord) {
    this.ord = ord;
  }
}
