package org.mzi.core.visitor;

import asia.kala.EmptyTuple;
import asia.kala.Tuple;
import asia.kala.collection.Set;
import asia.kala.collection.mutable.MutableSet;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Ref;
import org.mzi.core.def.FnDef;
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
    return unfold(fnCall).accept(this, Tuple.of());
  }

  static @NotNull Term unfold(AppTerm.@NotNull FnCall fnCall) {
    var def = fnCall.fnRef().def();
    // This shouldn't happen
    if (!(def instanceof FnDef fn)) return fnCall;
    assert fnCall.args().sizeEquals(fn.size());
    assert fn.telescope.checkSubst(fnCall.args());
    var subst = fn.telescope.buildSubst(fnCall.args());
    return fn.body.subst(subst);
  }
}
