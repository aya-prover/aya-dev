// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.visitor;

import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.tuple.Unit;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.Set;
import org.glavo.kala.collection.mutable.MutableSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Var;
import org.mzi.core.term.AppTerm;
import org.mzi.core.term.Term;
import org.mzi.generic.Arg;

import java.util.HashMap;

/**
 * @author ice1000
 */
public interface Unfolder<P> extends TermFixpoint<P> {
  @Contract(pure = true) static @NotNull Substituter.TermSubst buildSubst(
    @NotNull SeqLike<Term.@NotNull Param> self,
    @NotNull SeqLike<@NotNull ? extends @NotNull Arg<? extends Term>> args
  ) {
    var subst = new Substituter.TermSubst(new HashMap<>());
    self.forEachIndexed((i, param) -> subst.add(param.ref(), args.get(i).term()));
    return subst;
  }

  @Override default @NotNull Term visitFnCall(@NotNull AppTerm.FnCall fnCall, P p) {
    var def = fnCall.fnRef().core;
    // This shouldn't happen
    assert fnCall.args().sizeEquals(def.telescope().size());
    assert Term.Param.checkSubst(def.telescope(), fnCall.args());
    var subst = buildSubst(def.telescope(), fnCall.args());
    return def.body().subst(subst).accept(this, p);
  }

  /**
   * For tactics.
   *
   * @author ice1000
   */
  record Tracked(
    @NotNull Set<@NotNull Var> unfolding,
    @NotNull MutableSet<@NotNull Var> unfolded
  ) implements Unfolder<Unit> {
    @Override
    public @NotNull Term visitFnCall(AppTerm.@NotNull FnCall fnCall, Unit emptyTuple) {
      if (!unfolding.contains(fnCall.fnRef())) return fnCall;
      unfolded.add(fnCall.fnRef());
      return Unfolder.super.visitFnCall(fnCall, emptyTuple);
    }
  }
}
