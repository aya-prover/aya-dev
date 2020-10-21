package org.mzi.core.visitor;

import asia.kala.EmptyTuple;
import asia.kala.collection.Set;
import asia.kala.collection.mutable.MutableSet;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Ref;
import org.mzi.core.term.AppTerm;
import org.mzi.core.term.Term;

/**
 * For tactics.
 *
 * @author ice1000
 */
public class UnfoldFixpoint implements TermFixpoint<EmptyTuple> {
  private final Set<@NotNull Ref> unfolding;
  private final MutableSet<@NotNull Ref> unfolded;

  /**
   * @param unfolding definitions that we want to unfold.
   * @param unfolded  that we use to store the definitions that are actually unfolded.
   */
  public UnfoldFixpoint(@NotNull Set<@NotNull Ref> unfolding, @NotNull MutableSet<@NotNull Ref> unfolded) {
    this.unfolding = unfolding;
    this.unfolded = unfolded;
  }

  @Override
  public @NotNull Term visitFnCall(@NotNull AppTerm.FnCall fnCall, EmptyTuple emptyTuple) {
    if (!unfolding.contains(fnCall.fnRef())) return fnCall;
    unfolded.add(fnCall.fnRef());
    return unfold(fnCall, emptyTuple);
  }
}
