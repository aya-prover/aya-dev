// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.SeqLike;
import kala.collection.Set;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableSet;
import kala.tuple.Unit;
import org.aya.core.Matching;
import org.aya.core.def.PrimDef;
import org.aya.core.pat.PatMatcher;
import org.aya.core.term.CallTerm;
import org.aya.core.term.IntroTerm;
import org.aya.core.term.Term;
import org.aya.generic.Arg;
import org.aya.generic.Modifier;
import org.aya.ref.Var;
import org.aya.tyck.TyckState;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000
 */
public interface Unfolder<P> extends TermFixpoint<P> {
  @Nullable TyckState state();
  @Contract(pure = true) static @NotNull Substituter.TermSubst buildSubst(
    @NotNull SeqLike<Term.@NotNull Param> self,
    @NotNull SeqLike<@NotNull Arg<@NotNull Term>> args
  ) {
    var subst = new Substituter.TermSubst(MutableMap.create());
    self.view().zip(args).forEach(t -> subst.add(t._1.ref(), t._2.term()));
    return subst;
  }

  @Override @NotNull default Term visitConCall(CallTerm.@NotNull Con conCall, P p) {
    var def = conCall.ref().core;
    // Not yet type checked
    if (def == null) return conCall;
    var args = conCall.args().map(arg -> visitArg(arg, p));
    var ulift = ulift() + conCall.ulift();
    var dropped = args.drop(conCall.head().dataArgs().size());
    var volynskaya = tryUnfoldClauses(p, true, dropped, ulift, def.clauses);
    return volynskaya != null ? volynskaya.data() : new CallTerm.Con(conCall.head(), dropped);
  }

  @Override default @NotNull Term visitFnCall(@NotNull CallTerm.Fn fnCall, P p) {
    var def = fnCall.ref().core;
    // Not yet type checked
    if (def == null) return fnCall;
    var args = fnCall.args().map(arg -> visitArg(arg, p));
    var ulift = ulift() + fnCall.ulift();
    if (def.modifiers.contains(Modifier.Opaque)) return new CallTerm.Fn(fnCall.ref(), ulift, args);
    var body = def.body;
    if (body.isLeft()) {
      var termSubst = checkAndBuildSubst(def.telescope(), args);
      return body.getLeftValue().subst(termSubst, ulift).accept(this, p).rename();
    }
    var orderIndependent = def.modifiers.contains(Modifier.Overlap);
    var volynskaya = tryUnfoldClauses(p, orderIndependent, args, ulift, body.getRightValue());
    return volynskaya != null ? volynskaya.data().accept(this, p) : new CallTerm.Fn(fnCall.ref(), ulift, args);
  }
  private @NotNull Substituter.TermSubst
  checkAndBuildSubst(SeqLike<Term.Param> telescope, SeqLike<Arg<Term>> args) {
    // assert args.sizeEquals(telescope);
    // assert Term.Param.checkSubst(telescope, args);
    return buildSubst(telescope, args);
  }

  @Override @NotNull default Term visitPrimCall(@NotNull CallTerm.Prim prim, P p) {
    return PrimDef.Factory.INSTANCE.unfold(prim.id(), prim, state());
  }

  default @NotNull Term visitHole(@NotNull CallTerm.Hole hole, P p) {
    var def = hole.ref();
    // Not yet type checked
    var state = state();
    if (state == null) return hole;
    var metas = state.metas();
    if (!metas.containsKey(def)) return hole;
    var body = metas.get(def);
    var args = hole.fullArgs().map(arg -> visitArg(arg, p)).toImmutableSeq();
    var subst = checkAndBuildSubst(def.fullTelescope(), args);
    return body.subst(subst).accept(this, p);
  }

  default @Nullable WithPos<Term> tryUnfoldClauses(
    P p, boolean orderIndependent, SeqLike<Arg<Term>> args,
    int ulift, @NotNull ImmutableSeq<Matching> clauses
  ) {
    return tryUnfoldClauses(p, orderIndependent, args,
      new Substituter.TermSubst(MutableMap.create()), ulift, clauses);
  }

  default @Nullable WithPos<Term> tryUnfoldClauses(
    P p, boolean orderIndependent, SeqLike<Arg<Term>> args,
    Substituter.@NotNull TermSubst subst, int ulift,
    @NotNull ImmutableSeq<Matching> clauses
  ) {
    for (var matchy : clauses) {
      var termSubst = PatMatcher.tryBuildSubstArgs(null, matchy.patterns(), args);
      if (termSubst.isOk()) {
        subst.add(termSubst.get());
        var newBody = matchy.body().rename().subst(subst, ulift).accept(this, p);
        return new WithPos<>(matchy.sourcePos(), newBody);
      } else if (!orderIndependent && termSubst.getErr()) return null;
      // ^ if we have an order-dependent clause and the pattern matching is blocked,
      // we refuse to unfold the clauses (first-match semantics)
    }
    // Unfold failed
    return null;
  }

  default @NotNull Term visitAccess(CallTerm.@NotNull Access term, P p) {
    var nevv = term.of().accept(this, p);
    var fieldRef = term.ref();
    var fieldDef = fieldRef.core;
    if (!(nevv instanceof IntroTerm.New n)) {
      var args = term.args().map(arg -> visitArg(arg, p));
      var fieldSubst = checkAndBuildSubst(fieldDef.fullTelescope(), args);
      var structDef = fieldDef.structRef.core;
      var structArgsSize = term.structArgs().size();
      var ulift = ulift() + term.ulift();
      for (var field : structDef.fields) {
        if (field == fieldDef) continue;
        var tele = Term.Param.subst(field.telescope(), ulift);
        var access = new CallTerm.Access(nevv, field.ref, ulift, args.take(structArgsSize), tele.map(Term.Param::toArg));
        fieldSubst.add(field.ref, IntroTerm.Lambda.make(tele, access));
      }
      var dropped = args.drop(structArgsSize);
      var mischa = tryUnfoldClauses(p, true, dropped, fieldSubst, ulift, fieldDef.clauses);
      return mischa != null ? mischa.data().subst(fieldSubst, ulift) : new CallTerm.Access(nevv, fieldRef,
        ulift, term.structArgs(), dropped);
    }
    var arguments = buildSubst(fieldDef.ownerTele, term.structArgs());
    var fieldBody = term.fieldArgs().foldLeft(n.params().get(fieldRef), CallTerm::make);
    return fieldBody.subst(arguments).accept(this, p);
  }

  /**
   * For tactics.
   *
   * @author ice1000
   */
  record Tracked(
    @NotNull Set<@NotNull Var> unfolding,
    @NotNull MutableSet<@NotNull Var> unfolded,
    @Nullable TyckState state,
    @NotNull PrimDef.Factory factory
  ) implements Unfolder<Unit> {
    @Override public @NotNull Term visitFnCall(CallTerm.@NotNull Fn fnCall, Unit unit) {
      if (!unfolding.contains(fnCall.ref())) return fnCall;
      unfolded.add(fnCall.ref());
      return Unfolder.super.visitFnCall(fnCall, unit);
    }

    @Override public @NotNull Term visitConCall(@NotNull CallTerm.Con conCall, Unit unit) {
      if (!unfolding.contains(conCall.ref())) return conCall;
      unfolded.add(conCall.ref());
      return Unfolder.super.visitConCall(conCall, unit);
    }
  }
}
