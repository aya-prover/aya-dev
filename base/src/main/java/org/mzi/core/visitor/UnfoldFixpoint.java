// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.visitor;

import asia.kala.Unit;
import asia.kala.collection.Seq;
import asia.kala.collection.Set;
import asia.kala.collection.mutable.MutableSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Var;
import org.mzi.core.def.FnDef;
import org.mzi.core.term.AppTerm;
import org.mzi.core.term.Term;
import org.mzi.generic.Arg;
import org.mzi.generic.Tele;

import java.util.HashMap;

/**
 * @author ice1000
 */
public interface UnfoldFixpoint<P> extends TermFixpoint<P> {
  @Contract(pure = true) static @NotNull SubstFixpoint.TermSubst buildSubst(
    @NotNull Tele<Term> self,
    @NotNull Seq<@NotNull Arg<Term>> args
  ) {
    var subst = new SubstFixpoint.TermSubst(new HashMap<>());
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
    @NotNull Set<@NotNull Var> unfolding,
    @NotNull MutableSet<@NotNull Var> unfolded
  ) implements UnfoldFixpoint<Unit> {
    @Override
    public @NotNull Term visitFnCall(AppTerm.@NotNull FnCall fnCall, Unit emptyTuple) {
      if (!unfolding.contains(fnCall.fnRef())) return fnCall;
      unfolded.add(fnCall.fnRef());
      return UnfoldFixpoint.super.visitFnCall(fnCall, emptyTuple);
    }
  }
}
