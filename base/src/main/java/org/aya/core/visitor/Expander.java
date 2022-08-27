// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.SeqLike;
import kala.collection.Set;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableSet;
import kala.control.Option;
import kala.tuple.Tuple;
import org.aya.core.Matching;
import org.aya.core.def.PrimDef;
import org.aya.core.pat.PatMatcher;
import org.aya.core.term.*;
import org.aya.generic.Arg;
import org.aya.generic.Cube;
import org.aya.generic.Modifier;
import org.aya.guest0x0.cubical.CofThy;
import org.aya.guest0x0.cubical.Restr;
import org.aya.ref.Var;
import org.aya.tyck.TyckState;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public interface Expander extends EndoFunctor {
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
          var fieldBody = access.fieldArgs().foldLeft(n.params().get(access.ref()), CallTerm::make);
          yield apply(fieldBody.subst(buildSubst(fieldDef.ownerTele, access.structArgs())));
        } else {
          var subst = buildSubst(fieldDef.fullTelescope(), access.args());
          for (var field : fieldDef.structRef.core.fields) {
            if (field == fieldDef) continue;
            var fieldArgs = field.telescope().map(Term.Param::toArg);
            var acc = new CallTerm.Access(access.of(), field.ref, access.structArgs(), fieldArgs);
            subst.add(field.ref, IntroTerm.Lambda.make(field.telescope(), acc));
          }
          yield tryUnfoldClauses(true, access.fieldArgs(), subst, 0, fieldDef.clauses)
            .map(unfolded -> apply(unfolded.data())).getOrDefault(access);
        }
      }
      case RefTerm.MetaPat metaPat -> metaPat.inline();
      case PrimTerm.Mula mula -> simplFormula(mula);
      default -> term;
    };
  }
  static @NotNull Term simplFormula(@NotNull PrimTerm.Mula mula) {
    return Restr.formulae(mula.asFormula(), PrimTerm.Mula::new);
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

  default <T extends Term> @NotNull Term applyThoroughly(@NotNull Function<T, Term> f, @NotNull T term) {
    var applied = f.apply(term);
    return applied != term ? apply(applied) : applied;
  }

  record Normalizer(@NotNull TyckState state) implements Expander {
    @Override public @NotNull Term post(@NotNull Term term) {
      return switch (term) {
        case ElimTerm.App app -> applyThoroughly(CallTerm::make, app);
        case ElimTerm.Proj proj -> ElimTerm.proj(proj);
        case IntroTerm.PartEl el -> partial(el);
        case ElimTerm.PathApp app -> pathApp(app, this);
        default -> Expander.super.post(term);
      };
    }

    public @NotNull Restr<Term> applyRestr(@NotNull Restr<Term> restr) {
      return switch (restr.fmap(this)) {
        case Restr.Vary<Term> vary -> CofThy.normalizeRestr(vary);
        case Restr.Const<Term> c -> c;
      };
    }

  }
  static @NotNull IntroTerm.PartEl partial(@NotNull IntroTerm.PartEl el) {
    return switch (el) {
      case IntroTerm.SadPartEl par -> new IntroTerm.SadPartEl(par.u());
      case IntroTerm.HappyPartEl par -> {
        var clauses = MutableList.<Restr.Side<Term>>create();
        for (var clause : par.clauses()) {
          var u = clause.u();
          if (CofThy.normalizeCof(clause.cof(), clauses, cofib -> new Restr.Side<>(cofib, u))) {
            yield new IntroTerm.SadPartEl(u);
          }
        }
        yield new IntroTerm.HappyPartEl(clauses.toImmutableSeq(), par.rhsType());
      }
    };
  }

  static @NotNull Term pathApp(@NotNull ElimTerm.PathApp app, @NotNull Function<Term, Term> next) {
    if (app.of() instanceof IntroTerm.PathLam lam) {
      var xi = lam.params().map(Term.Param::ref);
      var ui = app.args().map(Arg::term);
      var subst = new Subst(xi, ui);
      return next.apply(lam.body().subst(subst));
    }
    return switch (Expander.partial(new IntroTerm.HappyPartEl(app.cube().clauses(), app.cube().type()))) {
      case IntroTerm.HappyPartEl el -> new ElimTerm.PathApp(app.of(), app.args(), new Cube<>(
        app.cube().params(), app.cube().type(), el.clauses()));
      case IntroTerm.SadPartEl el -> el.u();
    };
  }

  record WHNFer(@NotNull TyckState state) implements Expander {
    @Override public @NotNull Term post(@NotNull Term term) {
      return switch (term) {
        case ElimTerm.App app -> applyThoroughly(CallTerm::make, app);
        case ElimTerm.Proj proj -> ElimTerm.proj(proj);
        default -> Expander.super.post(term);
      };
    }

    @Override public @NotNull Term apply(@NotNull Term term) {
      return switch (term) {
        case IntroTerm.Lambda lambda -> lambda;
        case IntroTerm.Tuple tuple -> tuple;
        case FormTerm.Pi pi -> pi;
        case FormTerm.Sigma sigma -> sigma;
        case CallTerm.Data data -> data;
        default -> Expander.super.apply(term);
      };
    }
  }

  record Tracked(
    @NotNull Set<@NotNull Var> unfolding,
    @NotNull MutableSet<@NotNull Var> unfolded,
    @NotNull TyckState state,
    @NotNull PrimDef.Factory factory
  ) implements Expander {
    @Override public @NotNull Term apply(@NotNull Term term) {
      return switch (term) {
        case CallTerm.Fn fn -> {
          if (!unfolding.contains(fn.ref())) yield fn;
          unfolded.add(fn.ref());
          yield Expander.super.apply(fn);
        }
        case CallTerm.Con con -> {
          if (!unfolding.contains(con.ref())) yield con;
          unfolded.add(con.ref());
          yield Expander.super.apply(con);
        }
        case CallTerm.Prim prim -> factory.unfold(prim.id(), prim, state);
        default -> Expander.super.apply(term);
      };
    }
  }
}
