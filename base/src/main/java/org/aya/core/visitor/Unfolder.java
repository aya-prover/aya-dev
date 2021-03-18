// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import org.aya.api.ref.Var;
import org.aya.core.pat.Pat;
import org.aya.core.pat.PatMatcher;
import org.aya.core.term.CallTerm;
import org.aya.core.term.Term;
import org.aya.generic.Arg;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.Set;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.collection.mutable.MutableSet;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  @Override @NotNull default Term visitConCall(CallTerm.@NotNull Con conCall, P p) {
    var def = conCall.conHead().core;
    // Not yet type checked
    if (def == null) return conCall;
    var args = conCall.args().map(arg -> visitArg(arg, p)).toImmutableSeq();
    var tele = def.telescope().toImmutableSeq();
    assert args.sizeEquals(tele.size());
    assert Term.Param.checkSubst(tele, args);
    var volynskaya = tryUnfoldClauses(p, args, buildSubst(tele, args), def.clauses());
    return volynskaya != null ? volynskaya : conCall;
  }

  @Override default @NotNull Term visitFnCall(@NotNull CallTerm.Fn fnCall, P p) {
    var def = fnCall.fnRef().core;
    // Not yet type checked
    if (def == null) return fnCall;
    var args = fnCall.args().view().map(arg -> visitArg(arg, p)).toImmutableSeq();
    // This shouldn't fail
    assert args.sizeEquals(def.telescope().size());
    assert Term.Param.checkSubst(def.telescope(), args);
    var subst = buildSubst(def.telescope(), args);
    var body = def.body();
    if (body.isLeft()) return body.getLeftValue().subst(subst).accept(this, p);
    var volynskaya = tryUnfoldClauses(p, args, subst, body.getRightValue());
    return volynskaya != null ? volynskaya : fnCall;
  }

  private @Nullable Term tryUnfoldClauses(
    P p, ImmutableSeq<Arg<Term>> args,
    Substituter.@NotNull TermSubst subst,
    @NotNull ImmutableSeq<Pat.@NotNull Clause> clauses
  ) {
    for (var matchy : clauses) {
      var termSubst = PatMatcher.tryBuildSubst(matchy.patterns(), args);
      if (termSubst != null) {
        subst.add(termSubst);
        return matchy.expr().subst(subst).accept(this, p);
      }
    }
    // Unfold failed
    return null;
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
    public @NotNull Term visitFnCall(CallTerm.@NotNull Fn fnCall, Unit emptyTuple) {
      if (!unfolding.contains(fnCall.fnRef())) return fnCall;
      unfolded.add(fnCall.fnRef());
      return Unfolder.super.visitFnCall(fnCall, emptyTuple);
    }

    @Override public @NotNull Term visitConCall(@NotNull CallTerm.Con conCall, Unit unit) {
      if (!unfolding.contains(conCall.conHead())) return conCall;
      unfolded.add(conCall.conHead());
      return Unfolder.super.visitConCall(conCall, unit);
    }
  }
}
