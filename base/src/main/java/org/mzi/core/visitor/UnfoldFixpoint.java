package org.mzi.core.visitor;

import asia.kala.EmptyTuple;
import asia.kala.collection.Seq;
import asia.kala.collection.Set;
import asia.kala.collection.mutable.MutableSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Ref;
import org.mzi.core.def.FnDef;
import org.mzi.core.subst.TermSubst;
import org.mzi.core.term.AppTerm;
import org.mzi.core.term.Term;
import org.mzi.generic.Arg;
import org.mzi.generic.Tele;

import java.util.HashMap;

/**
 * @author ice1000
 */
public interface UnfoldFixpoint<P> extends TermFixpoint<P> {
  @Contract(pure = true)
  static @NotNull TermSubst buildSubst(
    @NotNull Tele<Term> self,
    @NotNull Seq<@NotNull Arg<Term>> args
  ) {
    var subst = new TermSubst(new HashMap<>());
    self.forEach((i, tele) -> subst.add(tele.ref(), args.get(i).term()));
    return subst;
  }

  @Override default @NotNull Term visitFnCall(@NotNull AppTerm.FnCall fnCall, P p) {
    var def = fnCall.fnRef().def();
    // This shouldn't happen
    if (!(def instanceof FnDef fn)) throw new IllegalStateException("`FnDef` expected, got: `" + def.getClass() + "`");
    assert fnCall.args().sizeEquals(fn.telescope.size());
    assert fn.telescope.checkSubst(fnCall.args());
    var subst = buildSubst(fn.telescope, fnCall.args());
    return fn.body.subst(subst).accept(this, p);
  }

  /**
   * For tactics.
   *
   * @author ice1000
   */
  record Tracked(
    @NotNull Set<@NotNull Ref> unfolding,
    @NotNull MutableSet<@NotNull Ref> unfolded
  ) implements UnfoldFixpoint<EmptyTuple> {
    @Override
    public @NotNull Term visitFnCall(AppTerm.@NotNull FnCall fnCall, EmptyTuple emptyTuple) {
      if (!unfolding.contains(fnCall.fnRef())) return fnCall;
      unfolded.add(fnCall.fnRef());
      return UnfoldFixpoint.super.visitFnCall(fnCall, emptyTuple);
    }
  }
}
