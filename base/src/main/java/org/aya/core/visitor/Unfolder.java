// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import org.aya.api.ref.Var;
import org.aya.api.util.Arg;
import org.aya.core.pat.Pat;
import org.aya.core.pat.PatMatcher;
import org.aya.core.term.CallTerm;
import org.aya.core.term.Term;
import org.aya.generic.Matching;
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
    var def = conCall.ref().core;
    // Not yet type checked
    if (def == null) return conCall;
    var args = conCall.fullArgs().map(arg -> visitArg(arg, p)).toImmutableSeq();
    var tele = def.fullTelescope();
    assert args.sizeEquals(tele.size());
    assert Term.Param.checkSubst(tele, args);
    var volynskaya = tryUnfoldClauses(p, args, buildSubst(tele, args), def.clauses());
    return volynskaya != null ? volynskaya : conCall;
  }

  @Override default @NotNull Term visitFnCall(@NotNull CallTerm.Fn fnCall, P p) {
    var def = fnCall.ref().core;
    // Not yet type checked
    if (def == null) return fnCall;
    var args = fnCall.fullArgs().view().map(arg -> visitArg(arg, p)).toImmutableSeq();
    // This shouldn't fail
    assert args.sizeEquals(def.fullTelescope().size());
    assert Term.Param.checkSubst(def.fullTelescope(), args);
    var subst = buildSubst(def.fullTelescope(), args);
    var body = def.body();
    if (body.isLeft()) return body.getLeftValue().subst(subst).accept(this, p);
    var volynskaya = tryUnfoldClauses(p, args, subst, body.getRightValue());
    return volynskaya != null ? volynskaya : fnCall;
  }

  @Override @NotNull default Term visitPrimCall(@NotNull CallTerm.Prim prim, P p) {
    return prim.ref().core.unfold(prim);
  }

  @Override default @NotNull Term visitHole(@NotNull CallTerm.Hole hole, P p) {
    var def = hole.ref().core();
    // Not yet type checked
    var args = hole.fullArgs().view().map(arg -> visitArg(arg, p)).toImmutableSeq();
    // This shouldn't fail
    assert args.sizeEquals(def.fullTelescope().size());
    assert Term.Param.checkSubst(def.fullTelescope(), args);
    var subst = buildSubst(def.fullTelescope(), args);
    var body = def.body;
    if (body == null) return hole;
    return body.subst(subst).accept(this, p);
  }

  default @Nullable Term tryUnfoldClauses(
    P p, ImmutableSeq<Arg<Term>> args,
    Substituter.@NotNull TermSubst subst,
    @NotNull ImmutableSeq<Matching<Pat, Term>> clauses
  ) {
    for (var matchy : clauses) {
      var termSubst = PatMatcher.tryBuildSubstArgs(matchy.patterns(), args);
      if (termSubst != null) {
        subst.add(termSubst);
        return matchy.body().subst(subst).accept(this, p);
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
      if (!unfolding.contains(fnCall.ref())) return fnCall;
      unfolded.add(fnCall.ref());
      return Unfolder.super.visitFnCall(fnCall, emptyTuple);
    }

    @Override public @NotNull Term visitConCall(@NotNull CallTerm.Con conCall, Unit unit) {
      if (!unfolding.contains(conCall.ref())) return conCall;
      unfolded.add(conCall.ref());
      return Unfolder.super.visitConCall(conCall, unit);
    }
  }
}
