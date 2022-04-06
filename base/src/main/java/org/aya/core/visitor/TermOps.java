// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.mutable.MutableList;
import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import org.aya.core.Matching;
import org.aya.core.def.PrimDef;
import org.aya.core.pat.PatMatcher;
import org.aya.core.term.*;
import org.aya.ref.Var;
import org.aya.generic.Arg;
import org.aya.generic.Modifier;
import org.aya.tyck.TyckState;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public interface TermOps extends TermView {
  @NotNull TermView view();
  @Override default @NotNull Term initial() {
    return view().initial();
  }

  record Mapper(
    @Override @NotNull TermView view,
    @NotNull Function<@NotNull Term, @NotNull Term> pre,
    @NotNull Function<@NotNull Term, @NotNull Term> post
  ) implements TermOps {
    @Override public TermView preMap(Function<Term, Term> f) {
      return new Mapper(view, pre.compose(f), post);
    }

    @Override public TermView postMap(Function<Term, Term> f) {
      return new Mapper(view, pre, f.compose(post));
    }

    @Override public Term pre(Term term) {
      return view.pre(pre.apply(term));
    }

    @Override public Term post(Term term) {
      return post.apply(view.post(term));
    }
  }

  record Subster(@Override @NotNull TermView view, @NotNull Subst subst) implements TermOps {
    @Override public @NotNull TermView subst(@NotNull Subst subst) {
      return new Subster(view, subst.add(subst));
    }

    @Override public Term post(Term term) {
      return switch (view.post(term)) {
        case RefTerm ref -> subst.map().getOption(ref.var()).map(Term::rename).getOrDefault(ref);
        case RefTerm.Field field -> subst.map().getOption(field.ref()).map(Term::rename).getOrDefault(field);
        case Term misc -> misc;
      };
    }
  }

  /** Not an IntelliJ Renamer. */
  record Renamer(@Override @NotNull TermView view, Subst subst) implements TermOps {
    public Renamer(@NotNull TermView view) {
      this(view, new Subst(MutableMap.create()));
    }

    private @NotNull Term.Param handleBinder(@NotNull Term.Param param) {
      var v = param.renameVar();
      subst.addDirectly(param.ref(), new RefTerm(v, 0));
      return new Term.Param(v, param.type(), param.pattern(), param.explicit());
    }

    @Override public Term pre(Term term) {
      return switch (view.pre(term)) {
        case IntroTerm.Lambda lambda -> new IntroTerm.Lambda(handleBinder(lambda.param()), lambda.body());
        case FormTerm.Pi pi -> new FormTerm.Pi(handleBinder(pi.param()), pi.body());
        case FormTerm.Sigma sigma -> new FormTerm.Sigma(sigma.params().map(this::handleBinder));
        case Term misc -> misc;
      };
    }

    @Override public Term post(Term term) {
      return switch (view.post(term)) {
        case RefTerm ref -> subst.map().getOrDefault(ref.var(), ref);
        // [ice]: need to generate "replacements" for 'this' bindings as well?
        case RefTerm.Field field -> subst.map().getOrDefault(field.ref(), field);
        case Term misc -> misc;
      };
    }
  }

  /** A lift but in American English. */
  record Elevator(@Override @NotNull TermView view, int ulift, MutableList<Var> boundVars) implements TermOps {
    public Elevator(@NotNull TermView view, int ulift) {
      this(view, ulift, MutableList.create());
    }

    @Override public @NotNull TermView lift(int shift) {
      return new Elevator(view, ulift + shift, boundVars);
    }

    public Term pre(Term term) {
      return view.pre(switch (term) {
        case IntroTerm.Lambda lambda -> {
          boundVars.append(lambda.param().ref());
          yield lambda;
        }
        case FormTerm.Pi pi -> {
          boundVars.append(pi.param().ref());
          yield pi;
        }
        case FormTerm.Sigma sigma -> {
          boundVars.appendAll(sigma.params().map(Term.Param::ref));
          yield sigma;
        }
        default -> term;
      });
    }

    public Term post(Term term) {
      return switch (view.post(term)) {
        case FormTerm.Univ univ -> new FormTerm.Univ(univ.lift() + ulift);
        case CallTerm.Struct struct -> new CallTerm.Struct(struct.ref(), struct.ulift() + ulift, struct.args());
        case CallTerm.Data data -> new CallTerm.Data(data.ref(), data.ulift() + ulift, data.args());
        case CallTerm.Con con -> {
          var head = con.head();
          head = new CallTerm.ConHead(head.dataRef(), head.ref(), head.ulift() + ulift, head.dataArgs());
          yield new CallTerm.Con(head, con.conArgs());
        }
        case CallTerm.Fn fn -> new CallTerm.Fn(fn.ref(), fn.ulift() + ulift, fn.args());
        case CallTerm.Prim prim -> new CallTerm.Prim(prim.ref(), prim.ulift() + ulift, prim.args());
        case CallTerm.Hole hole -> new CallTerm.Hole(hole.ref(), hole.ulift() + ulift, hole.contextArgs(), hole.args());
        case RefTerm ref -> boundVars.contains(ref.var())
          ? ref : new RefTerm(ref.var(), ref.lift() + ulift);
        case RefTerm.Field field -> boundVars.contains(field.ref())
          ? field : new RefTerm.Field(field.ref(), field.lift() + ulift);
        case Term misc -> misc;
      };
    }
  }

  record Normalizer(@NotNull @Override TermView view, TyckState state) implements TermOps {
    static private @NotNull Subst buildSubst(
      @NotNull SeqLike<Term.@NotNull Param> params,
      @NotNull SeqLike<@NotNull Arg<@NotNull Term>> args
    ) {
      var subst = new Subst(MutableMap.create());
      params.view().zip(args).forEach(t -> subst.add(t._1.ref(), t._2.term()));
      return subst;
    }

    private @Nullable WithPos<Term> unfoldClauses(
      boolean orderIndependent, SeqLike<Arg<Term>> args,
      @NotNull ImmutableSeq<Matching> clauses
    ) {
      return unfoldClauses(orderIndependent, args, new Subst(MutableMap.create()), clauses);
    }

    private @Nullable WithPos<Term> unfoldClauses(
      boolean orderIndependent, SeqLike<Arg<Term>> args,
      @NotNull Subst subst, @NotNull ImmutableSeq<Matching> clauses
    ) {
      for (var match : clauses) {
        var result = PatMatcher.tryBuildSubstArgs(null, match.patterns(), args);
        if (result.isOk()) {
          subst.add(result.get());
          var body = match.body().view().rename().subst(subst).normal(state).commit();
          return new WithPos<>(match.sourcePos(), body);
        } else if (!orderIndependent && result.getErr())
          return null;
      }
      return null;
    }

    @Override public Term post(Term term) {
      return switch (view.post(term)) {
        case ElimTerm.App app -> {
          var fn = app.of();
          if (fn instanceof IntroTerm.Lambda lambda)
            yield CallTerm.make(lambda, app.arg());
          else yield app;
        }
        case ElimTerm.Proj proj -> {
          var tup = proj.of();
          var ix = proj.ix();
          if (tup instanceof IntroTerm.Tuple tuple) {
            assert tuple.items().sizeGreaterThanOrEquals(ix) && ix > 0
              : proj.toDoc(DistillerOptions.debug()).debugRender();
            yield tuple.items().get(ix - 1);
          } else yield proj;
        }
        case CallTerm.Con con -> {
          var def = con.ref().core;
          if (def == null) yield con;
          var unfolded = unfoldClauses(true, con.conArgs(), def.clauses);
          yield unfolded != null ? unfolded.data() : con;
        }
        case CallTerm.Fn fn -> {
          var def = fn.ref().core;
          if (def == null) yield fn;
          if (def.modifiers.contains(Modifier.Opaque)) yield fn;
          yield def.body.fold(
            lamBody -> lamBody.view().subst(buildSubst(def.telescope(), fn.args())).rename().normal(state).commit(),
            patBody -> {
              var orderIndependent = def.modifiers.contains(Modifier.Overlap);
              var unfolded = unfoldClauses(orderIndependent, fn.args(), patBody);
              // CHECK: Do we need to normalize `unfolded` again?
              return unfolded != null ? unfolded.data() : fn;
            }
          );
        }
        case CallTerm.Access access -> {
          var fieldDef = access.ref().core;
          if (access.of() instanceof IntroTerm.New n) {
            var fieldBody = access.fieldArgs().foldLeft(n.params().get(access.ref()), CallTerm::make);
            yield fieldBody.view().subst(buildSubst(fieldDef.ownerTele, access.structArgs())).normal(state).commit();
          } else {
            var subst = buildSubst(fieldDef.fullTelescope(), access.args());
            for (var field : fieldDef.structRef.core.fields) {
              if (field == fieldDef) continue;
              var fieldArgs = field.telescope().map(Term.Param::toArg);
              var acc = new CallTerm.Access(access.of(), field.ref, access.structArgs(), fieldArgs);
              subst.add(field.ref, IntroTerm.Lambda.make(field.telescope(), acc));
            }
            var unfolded = unfoldClauses(true, access.fieldArgs(), subst, fieldDef.clauses);
            // CHECK: seems no need to substitute `unfolded` again with `subst`
            yield unfolded != null ? unfolded.data() : access;
          }
        }
        case CallTerm.Prim prim -> PrimDef.Factory.INSTANCE.unfold(prim.id(), prim, state);
        case CallTerm.Hole hole -> {
          var def = hole.ref();
          if (state == null || !state.metas().containsKey(def)) yield hole;
          var body = state.metas().get(def);
          yield body.view().subst(buildSubst(def.fullTelescope(), hole.fullArgs())).normal(state).commit();
        }
        case RefTerm.MetaPat metaPat -> metaPat.inline();
        case Term t -> t;
      };
    }
  }
}
