// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import org.aya.api.ref.Var;
import org.aya.core.pat.PatMatcher;
import org.aya.core.term.CallTerm;
import org.aya.core.term.Term;
import org.aya.generic.Arg;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.Set;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.collection.mutable.MutableSet;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public interface Unfolder<P> extends TermFixpoint<P> {
  @Contract(pure = true) static @NotNull Substituter.TermSubst buildSubst(
    @NotNull SeqLike<Term.@NotNull Param> self,
    @NotNull SeqLike<@NotNull Arg<@NotNull Term>> args
  ) {
    var subst = new Substituter.TermSubst(new MutableHashMap<>());
    self.forEachIndexed((i, param) -> subst.add(param.ref(), args.get(i).term()));
    return subst;
  }

  @Override default @NotNull Term visitFnCall(@NotNull CallTerm.FnCall fnCall, P p) {
    var def = fnCall.fnRef().core;
    var args = fnCall.args();
    // This shouldn't fail
    assert args.sizeEquals(def.telescope().size());
    assert Term.Param.checkSubst(def.telescope(), args);
    var subst = buildSubst(def.telescope(), args);
    var body = def.body();
    if (body.isLeft()) return body.getLeftValue().subst(subst).accept(this, p);
    var clauses = body.getRightValue();
    for (var matchy : clauses) {
      var termSubst = PatMatcher.tryBuildSubst(matchy.patterns(), args);
      if (termSubst != null) {
        subst.add(termSubst);
        return matchy.expr().subst(subst);
      }
    }
    // Unfold failed
    return fnCall;
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
    public @NotNull Term visitFnCall(CallTerm.@NotNull FnCall fnCall, Unit emptyTuple) {
      if (!unfolding.contains(fnCall.fnRef())) return fnCall;
      unfolded.add(fnCall.fnRef());
      return Unfolder.super.visitFnCall(fnCall, emptyTuple);
    }
  }
}
