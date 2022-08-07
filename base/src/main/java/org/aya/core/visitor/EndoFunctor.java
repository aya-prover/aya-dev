// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import org.aya.core.Matching;
import org.aya.core.pat.PatMatcher;
import org.aya.core.term.*;
import org.aya.generic.Arg;
import org.aya.generic.Modifier;
import org.aya.generic.util.InternalException;
import org.aya.ref.LocalVar;
import org.aya.ref.Var;
import org.aya.tyck.TyckState;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * A convenient interface to obtain an endofunction on `Term`.
 * Either the operation of folding to a term, or that of unfolding from a term can be regarded as an endofunction.
 * Composing the above two operations also gives an endofunction,
 * and this is in fact what this interface provides by implementing both `Folder<Term>` and `Unfolder<Term>`.
 * In this spirit, the user can provide `fold : Tm<Term> -> Term` and `unfold : Term -> Tm<Term>`.
 * But since `Tm<Term>` and `Term` are isomorphic,
 * we instead ask for `pre : Term -> Term` and `post : Term -> Term`,
 * as this would allow a specialized implementation eliminating unnecessary casting,
 * and dealing with purely `Term`s can be more convenient.
 * <p>
 * The `act : Term -> Term` method should be called for the final composed action on `Term`.
 * Although it is essentially the composition of the derived `unfolded` and `folded` functions,
 * `act` has better performance by eliminating casting completely,
 * and attempts to preserve object identity when possible.
 * The implementation of `pre` and `post` can also take advantage of this behavior.
 *
 * @author wsx
 */
public interface EndoFunctor extends Function<Term, Term> {
  default Term pre(Term term) {
    return term;
  }

  default Term post(Term term) {
    return term;
  }

  default Term apply(Term term) {
    return post(pre(term).descent(this));
  }

  /** Not an IntelliJ Renamer. */
  record Renamer(Subst subst) implements EndoFunctor {
    public Renamer() {
      this(new Subst(MutableMap.create()));
    }

    private @NotNull Term.Param handleBinder(@NotNull Term.Param param) {
      var v = param.renameVar();
      subst.addDirectly(param.ref(), new RefTerm(v, 0));
      return new Term.Param(v, param.type(), param.pattern(), param.explicit());
    }

    @Override public Term pre(Term term) {
      return switch (term) {
        case IntroTerm.Lambda lambda -> new IntroTerm.Lambda(handleBinder(lambda.param()), lambda.body());
        case FormTerm.Pi pi -> new FormTerm.Pi(handleBinder(pi.param()), pi.body());
        case FormTerm.Sigma sigma -> new FormTerm.Sigma(sigma.params().map(this::handleBinder));
        case RefTerm ref -> subst.map().getOrDefault(ref.var(), ref);
        case RefTerm.Field field -> subst.map().getOrDefault(field.ref(), field);
        case Term misc -> misc;
      };
    }
  }

  /**
   * Performes capture-avoiding substitution.
   */
  record Substituter(Subst subst) implements EndoFunctor {
    @Override public Term post(Term term) {
      return switch (term) {
        case RefTerm ref && ref.var() == LocalVar.IGNORED -> throw new InternalException("found usage of ignored var");
        case RefTerm ref -> subst.map().getOption(ref.var()).map(Term::rename).getOrDefault(ref);
        case RefTerm.Field field -> subst.map().getOption(field.ref()).map(Term::rename).getOrDefault(field);
        case Term misc -> misc;
      };
    }
  }

  /** A lift but in American English. */
  record Elevator(int lift, MutableList<Var> boundVars) implements EndoFunctor {
    public Elevator(int lift) {
      this(lift, MutableList.create());
    }

    @Override public Term pre(Term term) {
      switch (term) {
        case FormTerm.Pi pi -> boundVars.append(pi.param().ref());
        case FormTerm.Sigma sigma -> boundVars.appendAll(sigma.params().map(Term.Param::ref));
        case IntroTerm.Lambda lambda -> boundVars.append(lambda.param().ref());
        default -> {}
      }
      return term;
    }

    @Override public Term post(Term term) {
      return switch (term) {
        case FormTerm.Univ univ -> new FormTerm.Univ(univ.lift() + lift);
        case CallTerm.Struct struct -> new CallTerm.Struct(struct.ref(), struct.ulift() + lift, struct.args());
        case CallTerm.Data data -> new CallTerm.Data(data.ref(), data.ulift() + lift, data.args());
        case CallTerm.Con con -> {
          var head = con.head();
          head = new CallTerm.ConHead(head.dataRef(), head.ref(), head.ulift() + lift, head.dataArgs());
          yield new CallTerm.Con(head, con.conArgs());
        }
        case CallTerm.Fn fn -> new CallTerm.Fn(fn.ref(), fn.ulift() + lift, fn.args());
        case CallTerm.Prim prim -> new CallTerm.Prim(prim.ref(), prim.ulift() + lift, prim.args());
        case CallTerm.Hole hole -> new CallTerm.Hole(hole.ref(), hole.ulift() + lift, hole.contextArgs(), hole.args());
        case RefTerm ref -> boundVars.contains(ref.var())
          ? ref : new RefTerm(ref.var(), ref.lift() + lift);
        case RefTerm.Field field -> boundVars.contains(field.ref())
          ? field : new RefTerm.Field(field.ref(), field.lift() + lift);
        case Term misc -> misc;
      };
    }
  }

  record Normalizer(TyckState state) implements EndoFunctor {
    @Override public Term post(Term term) {
      return switch (term) {
        case ElimTerm.App app && app.of() instanceof IntroTerm.Lambda lambda -> apply(CallTerm.make(lambda, app.arg()));
        case ElimTerm.Proj proj && proj.of() instanceof IntroTerm.Tuple tuple -> {
          var ix = proj.ix();
          assert tuple.items().sizeGreaterThanOrEquals(ix) && ix > 0
            : proj.toDoc(DistillerOptions.debug()).debugRender();
          yield tuple.items().get(ix - 1);
        }
        case CallTerm.Con con -> {
          var def = con.ref().core;
          if (def == null) yield con;
          var unfolded = unfoldClauses(true, con.conArgs(), def.clauses);
          yield unfolded != null ? apply(unfolded.data()) : con;
        }
        case CallTerm.Fn fn -> {
          var def = fn.ref().core;
          if (def == null) yield fn;
          if (def.modifiers.contains(Modifier.Opaque)) yield fn;
          yield def.body.fold(
            lamBody -> apply(lamBody.subst(buildSubst(def.telescope(), fn.args()))),
            patBody -> {
              var unfolded = unfoldClauses(def.modifiers.contains(Modifier.Overlap), fn.args(), patBody);
              return unfolded != null ? apply(unfolded.data()) : fn;
            }
          );
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
            var unfolded = unfoldClauses(true, access.fieldArgs(), subst, fieldDef.clauses);
            yield unfolded != null ? apply(unfolded.data()) : access;
          }
        }
        case CallTerm.Prim prim -> state.primFactory().unfold(prim.id(), prim, state);
        case CallTerm.Hole hole -> {
          var def = hole.ref();
          if (!state.metas().containsKey(def)) yield hole;
          var body = state.metas().get(def);
          yield apply(body.subst(buildSubst(def.fullTelescope(), hole.fullArgs())));
        }
        case RefTerm.MetaPat metaPat -> metaPat.inline();
        case Term t -> t;
      };
    }

    static private Subst buildSubst(SeqLike<Term.Param> params, SeqLike<Arg<Term>> args) {
      var subst = new Subst(MutableMap.create());
      params.view().zip(args).forEach(t -> subst.add(t._1.ref(), t._2.term()));
      return subst;
    }

    private @Nullable WithPos<Term> unfoldClauses(
      boolean orderIndependent, SeqLike<Arg<Term>> args,
      ImmutableSeq<Matching> clauses
    ) {
      return unfoldClauses(orderIndependent, args, new Subst(MutableMap.create()), clauses);
    }

    private @Nullable WithPos<Term> unfoldClauses(
      boolean orderIndependent, SeqLike<Arg<Term>> args,
      Subst subst, ImmutableSeq<Matching> clauses
    ) {
      for (var match : clauses) {
        var result = PatMatcher.tryBuildSubstArgs(null, match.patterns(), args);
        if (result.isOk()) {
          subst.add(result.get());
          var body = match.body().subst(subst);
          return new WithPos<>(match.sourcePos(), body);
        } else if (!orderIndependent && result.getErr())
          return null;
      }
      return null;
    }
  }
}
