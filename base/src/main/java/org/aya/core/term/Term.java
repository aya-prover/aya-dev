// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.Map;
import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.tuple.Tuple3;
import kala.tuple.Unit;
import org.aya.api.core.CoreTerm;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.ref.Bind;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.api.util.Arg;
import org.aya.api.util.NormalizeMode;
import org.aya.core.ops.TermToPat;
import org.aya.core.pat.Pat;
import org.aya.core.sort.LevelSubst;
import org.aya.core.sort.Sort;
import org.aya.core.visitor.*;
import org.aya.distill.BaseDistiller;
import org.aya.distill.CoreDistiller;
import org.aya.generic.ParamLike;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.LittleTyper;
import org.aya.tyck.TyckState;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * A well-typed and terminating term.
 *
 * @author ice1000
 */
public sealed interface Term extends CoreTerm permits
  CallTerm, ElimTerm, ErrorTerm, FormTerm, IntroTerm,
  RefTerm, RefTerm.Field, RefTerm.MetaPat {
  <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p);

  default <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    visitor.traceEntrance(this, p);
    var ret = doAccept(visitor, p);
    visitor.traceExit(ret);
    return ret;
  }

  @Override default @Nullable Pat toPat(boolean explicit) {
    return TermToPat.toPat(this, explicit);
  }

  default @NotNull Term subst(@NotNull Var var, @NotNull Term term) {
    return subst(new Substituter.TermSubst(var, term));
  }

  default @NotNull Term subst(@NotNull Substituter.TermSubst subst) {
    return subst(subst, LevelSubst.EMPTY);
  }

  default @NotNull Term subst(@NotNull Map<Var, Term> subst) {
    return accept(new Substituter(subst, LevelSubst.EMPTY), Unit.unit());
  }

  default @NotNull Term subst(@NotNull Substituter.TermSubst subst, @NotNull LevelSubst levelSubst) {
    return accept(new Substituter(subst, levelSubst), Unit.unit());
  }

  default @NotNull Term zonk(@NotNull ExprTycker tycker, @Nullable SourcePos pos) {
    return tycker.newZonker().zonk(this, pos);
  }

  @Override default @NotNull Term rename() {
    return accept(new Renamer(), Unit.unit());
  }

  @Override default int findUsages(@NotNull Var var) {
    var counter = new VarConsumer.UsageCounter(var);
    accept(counter, Unit.unit());
    return counter.usageCount();
  }

  @Override default @NotNull DynamicSeq<LocalVar> scopeCheck(@NotNull ImmutableSeq<LocalVar> allowed) {
    var checker = new VarConsumer.ScopeChecker(allowed);
    accept(checker, Unit.unit());
    assert checker.isCleared() : "The scope checker is not properly cleared up";
    return checker.invalidVars;
  }

  default @NotNull Term normalize(@Nullable TyckState state, @NotNull NormalizeMode mode) {
    if (mode == NormalizeMode.NULL) return this;
    return accept(new Normalizer(state), mode);
  }

  default @NotNull Term freezeHoles(@Nullable TyckState state) {
    return accept(new TermFixpoint<>() {
      @Override public @NotNull Term visitHole(CallTerm.@NotNull Hole term, Unit unit) {
        if (state == null) return TermFixpoint.super.visitHole(term, unit);
        var sol = term.ref();
        var metas = state.metas();
        if (!metas.containsKey(sol)) return TermFixpoint.super.visitHole(term, unit);
        return metas.get(sol).accept(this, Unit.unit());
      }

      @Override public @NotNull Sort visitSort(@NotNull Sort sort, Unit unit) {
        return state != null ? state.levelEqns().applyTo(sort) : sort;
      }
    }, Unit.unit());
  }

  @Override default @NotNull Doc toDoc(@NotNull DistillerOptions options) {
    return accept(new CoreDistiller(options), BaseDistiller.Outer.Free);
  }
  default @NotNull Term computeType(@Nullable TyckState state) {
    return accept(new LittleTyper(state), Unit.unit());
  }

  interface Visitor<P, R> {
    default void traceEntrance(@NotNull Term term, P p) {
    }
    default void traceExit(R r) {
    }
    R visitRef(@NotNull RefTerm ref, P p);
    R visitLam(@NotNull IntroTerm.Lambda lambda, P p);
    R visitPi(@NotNull FormTerm.Pi pi, P p);
    R visitSigma(@NotNull FormTerm.Sigma sigma, P p);
    R visitUniv(@NotNull FormTerm.Univ univ, P p);
    R visitApp(@NotNull ElimTerm.App app, P p);
    R visitFnCall(CallTerm.@NotNull Fn fnCall, P p);
    R visitDataCall(CallTerm.@NotNull Data dataCall, P p);
    R visitConCall(CallTerm.@NotNull Con conCall, P p);
    R visitStructCall(CallTerm.@NotNull Struct structCall, P p);
    R visitPrimCall(@NotNull CallTerm.Prim prim, P p);
    R visitTup(@NotNull IntroTerm.Tuple tuple, P p);
    R visitNew(@NotNull IntroTerm.New newTerm, P p);
    R visitProj(@NotNull ElimTerm.Proj proj, P p);
    R visitAccess(@NotNull CallTerm.Access access, P p);
    R visitHole(@NotNull CallTerm.Hole hole, P p);
    R visitFieldRef(@NotNull RefTerm.Field field, P p);
    R visitError(@NotNull ErrorTerm error, P p);
    R visitMetaPat(@NotNull RefTerm.MetaPat metaPat, P p);
  }

  /**
   * @author re-xyr
   */
  record Param(
    @NotNull LocalVar ref,
    @NotNull Term type,
    boolean pattern,
    boolean explicit
  ) implements Bind, ParamLike<Term> {
    public Param(@NotNull LocalVar ref, @NotNull Term type, boolean explicit) {
      this(ref, type, false, explicit);
    }

    public Param(@NotNull ParamLike<?> param, @NotNull Term type) {
      this(param.ref(), type, param.pattern(), param.explicit());
    }

    public static @NotNull ImmutableSeq<@NotNull Param> fromBuffer(DynamicSeq<Tuple3<LocalVar, Boolean, Term>> buf) {
      return buf.view().map(tup -> new Param(tup._1, tup._3, false, tup._2)).toImmutableSeq();
    }

    @Contract(" -> new") public @NotNull Param implicitify() {
      return new Param(ref, type, pattern, false);
    }

    @Contract(" -> new") public @NotNull Param rename() {
      return new Param(renameVar(), type, pattern, explicit);
    }

    @Contract(" -> new") public @NotNull LocalVar renameVar() {
      return new LocalVar(ref.name(), ref.definition());
    }

    @Override @Contract(" -> new") public @NotNull Arg<@NotNull Term> toArg() {
      return new Arg<>(toTerm(), explicit);
    }

    @Contract(" -> new") public @NotNull RefTerm toTerm() {
      return new RefTerm(ref, type);
    }

    public @NotNull Param subst(@NotNull Var var, @NotNull Term term) {
      return subst(new Substituter.TermSubst(var, term));
    }

    public @NotNull Param subst(@NotNull Substituter.TermSubst subst) {
      return subst(subst, LevelSubst.EMPTY);
    }

    public static @NotNull ImmutableSeq<Param> subst(
      @NotNull ImmutableSeq<@NotNull Param> params,
      @NotNull Substituter.TermSubst subst, @NotNull LevelSubst levelSubst
    ) {
      return params.map(param -> param.subst(subst, levelSubst));
    }

    public static @NotNull ImmutableSeq<Param>
    subst(@NotNull ImmutableSeq<@NotNull Param> params, @NotNull LevelSubst levelSubst) {
      return subst(params, Substituter.TermSubst.EMPTY, levelSubst);
    }

    public @NotNull Param subst(@NotNull Substituter.TermSubst subst, @NotNull LevelSubst levelSubst) {
      return new Param(ref, type.subst(subst, levelSubst), pattern, explicit);
    }

    @TestOnly @Contract(pure = true)
    public static boolean checkSubst(@NotNull SeqLike<@NotNull Param> params, @NotNull SeqLike<Arg<Term>> args) {
      var obj = new Object() {
        boolean ok = true;
      };
      params.forEachIndexed((i, param) -> obj.ok = obj.ok && param.explicit() == args.get(i).explicit());
      return obj.ok;
    }
  }

}
