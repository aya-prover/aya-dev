// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import kala.tuple.Tuple;
import org.aya.core.Matching;
import org.aya.core.pat.PatMatcher;
import org.aya.core.term.CallTerm;
import org.aya.core.term.ElimTerm;
import org.aya.core.term.IntroTerm;
import org.aya.core.term.Term;
import org.aya.generic.Arg;
import org.aya.generic.Modifier;
import org.aya.tyck.TyckState;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;

/**
 * @author wsx
 * @see BetaExpander
 */
public interface DeltaExpander extends EndoFunctor {
  @NotNull TyckState state();

  static @NotNull Subst buildSubst(@NotNull SeqLike<Term.Param> self, @NotNull SeqLike<Arg<Term>> args) {
    var entries = self.view().zip(args)
      .map(t -> Tuple.of(t._1.ref(), t._2.term()));
    return new Subst(MutableMap.from(entries));
  }

  @Override default @NotNull Term post(@NotNull Term term) {
    return switch (term) {
      case CallTerm.Con con -> {
        var def = con.ref().core;
        if (def == null) yield con;
        yield tryUnfoldClauses(true, con.conArgs(), con.ulift(), def.clauses)
          .map(un -> apply(un.data())).getOrDefault(con);
      }
      case CallTerm.Fn fn -> {
        var def = fn.ref().core;
        if (def == null || def.modifiers.contains(Modifier.Opaque)) yield fn;
        yield def.body.fold(
          lamBody -> apply(lamBody.rename().subst(buildSubst(def.telescope(), fn.args())).lift(fn.ulift())),
          clauses -> tryUnfoldClauses(def.modifiers.contains(Modifier.Overlap), fn.args(), fn.ulift(), clauses)
            .map(unfolded -> apply(unfolded.data())).getOrDefault(fn));
      }
      case CallTerm.Prim prim -> state().primFactory().unfold(prim.id(), prim, state());
      case CallTerm.Hole hole -> {
        var def = hole.ref();
        yield state().metas().getOption(def)
          .map(body -> apply(body.subst(buildSubst(def.fullTelescope(), hole.fullArgs()))))
          .getOrDefault(hole);
      }
      case CallTerm.Access access -> {
        var fieldDef = access.ref().core;
        if (access.of() instanceof IntroTerm.New n) {
          var fieldBody = access.fieldArgs().foldLeft(n.params().get(access.ref()), ElimTerm::make);
          yield apply(fieldBody.subst(buildSubst(fieldDef.ownerTele, access.structArgs())));
        }
        yield access;
      }
      default -> term;
    };
  }

  default @NotNull Option<WithPos<Term>> tryUnfoldClauses(
    boolean orderIndependent, @NotNull SeqLike<Arg<Term>> args,
    @NotNull Subst subst, int ulift, @NotNull ImmutableSeq<Matching> clauses
  ) {
    for (var matchy : clauses) {
      var termSubst = PatMatcher.tryBuildSubstArgs(null, matchy.patterns(), args);
      if (termSubst.isOk()) {
        subst.add(termSubst.get());
        var newBody = matchy.body().rename().subst(subst).lift(ulift);
        return Option.some(new WithPos<>(matchy.sourcePos(), newBody));
      } else if (!orderIndependent && termSubst.getErr()) return Option.none();
    }
    return Option.none();
  }
  default @NotNull Option<WithPos<Term>> tryUnfoldClauses(
    boolean orderIndependent, @NotNull SeqLike<Arg<Term>> args,
    int ulift, @NotNull ImmutableSeq<Matching> clauses
  ) {
    return tryUnfoldClauses(orderIndependent, args, new Subst(MutableMap.create()), ulift, clauses);
  }
}
