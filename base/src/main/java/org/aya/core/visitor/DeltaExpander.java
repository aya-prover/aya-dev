// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import kala.tuple.Tuple;
import org.aya.core.pat.PatMatcher;
import org.aya.core.term.*;
import org.aya.generic.Modifier;
import org.aya.guest0x0.cubical.Partial;
import org.aya.tyck.TyckState;
import org.aya.util.Arg;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;

/**
 * @author wsx
 * @see BetaExpander
 */
public interface DeltaExpander extends EndoTerm {
  @NotNull TyckState state();

  static @NotNull Subst buildSubst(@NotNull SeqLike<Term.Param> self, @NotNull SeqLike<Arg<Term>> args) {
    assert self.sizeEquals(args);
    return new Subst(MutableMap.from(
      self.zipView(args).map(t -> Tuple.of(t._1.ref(), t._2.term()))));
  }

  @Override default @NotNull Term post(@NotNull Term term) {
    return switch (term) {
      case ConCall con -> {
        var def = con.ref().core;
        if (def == null) yield con;
        var sat = AyaRestrSimplifier.INSTANCE.mapSplit(def.clauses, t ->
          t.subst(buildSubst(def.fullTelescope(), con.args())));
        if (sat instanceof Partial.Const<Term> c) yield apply(c.u());
        yield con;
      }
      case FnCall fn -> {
        var def = fn.ref().core;
        if (def == null || def.modifiers.contains(Modifier.Opaque)) yield fn;
        yield def.body.fold(
          lamBody -> apply(lamBody.rename().lift(fn.ulift()).subst(buildSubst(def.telescope(), fn.args()))),
          clauses -> tryUnfoldClauses(def.modifiers.contains(Modifier.Overlap), fn.args(), fn.ulift(), clauses)
            .map(unfolded -> apply(unfolded.data())).getOrDefault(fn));
      }
      case PrimCall prim -> state().primFactory().unfold(prim.id(), prim, state());
      case MetaTerm hole -> {
        var def = hole.ref();
        yield state().metas().getOption(def)
          .map(body -> apply(body.subst(buildSubst(def.fullTelescope(), hole.fullArgs()))))
          .getOrDefault(hole);
      }
      case FieldTerm access -> {
        var fieldDef = access.ref().core;
        if (access.of() instanceof NewTerm n) {
          var fieldBody = access.fieldArgs().foldLeft(n.params().get(access.ref()), AppTerm::make);
          yield apply(fieldBody.subst(buildSubst(fieldDef.ownerTele, access.structArgs())));
        }
        yield access;
      }
      default -> term;
    };
  }

  default @NotNull Option<WithPos<Term>> tryUnfoldClauses(
    boolean orderIndependent, @NotNull ImmutableSeq<Arg<Term>> args,
    int ulift, @NotNull ImmutableSeq<Term.Matching> clauses
  ) {
    for (var matchy : clauses) {
      var subst = PatMatcher.tryBuildSubst(false, matchy.patterns(), args, this);
      if (subst.isOk()) {
        var newBody = matchy.body().rename().lift(ulift).subst(subst.get());
        return Option.some(new WithPos<>(matchy.sourcePos(), newBody));
      } else if (!orderIndependent && subst.getErr()) return Option.none();
    }
    return Option.none();
  }
}
