// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.unify;

import kala.collection.SeqLike;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple2;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.api.util.Arg;
import org.aya.api.util.NormalizeMode;
import org.aya.core.term.*;
import org.aya.core.visitor.Substituter;
import org.aya.tyck.trace.Trace;
import org.aya.tyck.unify.level.LevelEqnSet;
import org.aya.util.Ordering;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author ice1000
 * @implNote Do not call <code>expr.accept(this, bla)</code> directly.
 * Instead, invoke {@link TypedDefEq#compare(Term, Term, Term)} to do so.
 */
public final class TypedDefEq {
  final @NotNull MutableMap<@NotNull LocalVar, @NotNull RefTerm> varSubst = new MutableHashMap<>();
  private final @Nullable Trace.Builder traceBuilder;
  public final @NotNull UntypedDefEq termDefeq;
  boolean allowVague;
  public final @NotNull LevelEqnSet levelEqns;
  public final @NotNull EqnSet termEqns;
  public final @NotNull Reporter reporter;
  public final @NotNull SourcePos pos;

  void tracing(@NotNull Consumer<Trace.@NotNull Builder> consumer) {
    if (traceBuilder != null) consumer.accept(traceBuilder);
  }

  void traceEntrance(@NotNull Trace trace) {
    tracing(builder -> builder.shift(trace));
  }

  private boolean accept(@NotNull Term type, @NotNull Term lhs, @NotNull Term rhs) {
    traceEntrance(new Trace.UnifyT(lhs.freezeHoles(levelEqns), rhs.freezeHoles(levelEqns),
      pos, type.freezeHoles(levelEqns)));
    var ret = switch (type) {
      case RefTerm type1 -> termDefeq.compare(lhs, rhs) != null;
      case FormTerm.Univ type1 -> termDefeq.compare(lhs, rhs) != null;
      case ElimTerm.App type1 -> termDefeq.compare(lhs, rhs) != null;
      case CallTerm.Fn type1 -> termDefeq.compare(lhs, rhs) != null;
      case CallTerm.Data type1 -> termDefeq.compare(lhs, rhs) != null;
      case CallTerm.Prim type1 -> termDefeq.compare(lhs, rhs) != null;
      case ElimTerm.Proj type1 -> termDefeq.compare(lhs, rhs) != null;
      case CallTerm.Access type1 -> termDefeq.compare(lhs, rhs) != null;
      case CallTerm.Hole type1 -> termDefeq.compare(lhs, rhs) != null;
      case CallTerm.Struct type1 -> {
        var fieldSigs = type1.ref().core.fields;
        var paramSubst = type1.ref().core.telescope().view().zip(type1.args().view()).map(x ->
          Tuple2.of(x._1.ref(), x._2.term())).<Var, Term>toImmutableMap();
        var fieldSubst = new Substituter.TermSubst(MutableHashMap.of());
        for (var fieldSig : fieldSigs) {
          var dummyVars = fieldSig.selfTele.map(par ->
            new LocalVar(par.ref().name(), par.ref().definition()));
          var dummy = dummyVars.zip(fieldSig.selfTele).map(vpa ->
            new Arg<Term>(new RefTerm(vpa._1, vpa._2.type()), vpa._2.explicit()));
          var l = new CallTerm.Access(lhs, fieldSig.ref(), type1.sortArgs(), type1.args(), dummy);
          var r = new CallTerm.Access(rhs, fieldSig.ref(), type1.sortArgs(), type1.args(), dummy);
          fieldSubst.add(fieldSig.ref(), l);
          if (!compare(l, r, fieldSig.result().subst(paramSubst).subst(fieldSubst))) yield false;
        }
        yield true;
      }
      case IntroTerm.Lambda type1 -> throw new IllegalStateException("LamTerm is never type");
      case CallTerm.Con type1 -> throw new IllegalStateException("ConCall is never type");
      case IntroTerm.Tuple type1 -> throw new IllegalStateException("TupTerm is never type");
      case IntroTerm.New newTerm -> throw new IllegalStateException("NewTerm is never type");
      case ErrorTerm term -> true;
      case FormTerm.Sigma type1 -> {
        var params = type1.params().view();
        for (int i = 1, size = type1.params().size(); i <= size; i++) {
          var l = new ElimTerm.Proj(lhs, i);
          var currentParam = params.first();
          if (!compare(l, new ElimTerm.Proj(rhs, i), currentParam.type())) yield false;
          params = params.drop(1).map(x -> x.subst(currentParam.ref(), l));
        }
        yield true;
      }
      case FormTerm.Pi type1 -> {
        var dummyVar = new LocalVar("dummy");
        var ty = type1.param().type();
        var dummy = new RefTerm(dummyVar, ty);
        var dummyArg = new Arg<Term>(dummy, type1.param().explicit());
        yield compare(CallTerm.make(lhs, dummyArg), CallTerm.make(rhs, dummyArg), type1.substBody(dummy));
      }
    };
    traceExit();
    return ret;
  }

  public void traceExit() {
    tracing(Trace.Builder::reduce);
  }

  public TypedDefEq(
    @NotNull Ordering cmp, @NotNull Reporter reporter, boolean allowVague,
    @NotNull LevelEqnSet levelEqns, @NotNull EqnSet termEqns,
    @Nullable Trace.Builder traceBuilder, @NotNull SourcePos pos
  ) {
    this.allowVague = allowVague;
    this.levelEqns = levelEqns;
    this.termEqns = termEqns;
    this.reporter = reporter;
    this.traceBuilder = traceBuilder;
    this.pos = pos;
    this.termDefeq = new UntypedDefEq(this, cmp);
  }

  public boolean compare(@NotNull Term lhs, @NotNull Term rhs, @NotNull Term type) {
    if (lhs == rhs) return true;
    if (termDefeq.compareApprox(lhs, rhs) != null) return true;
    type = type.normalize(NormalizeMode.WHNF);
    lhs = lhs.normalize(NormalizeMode.WHNF);
    rhs = rhs.normalize(NormalizeMode.WHNF);
    if (termDefeq.compareApprox(lhs, rhs) != null) return true;
    if (rhs instanceof CallTerm.Hole) return accept(type, rhs, lhs);
    return accept(type, lhs, rhs);
  }

  public static boolean isCall(@NotNull Term term) {
    return term instanceof CallTerm.Fn || term instanceof CallTerm.Con || term instanceof CallTerm.Prim;
  }

  public boolean compareWHNF(Term lhs, Term preRhs, @NotNull Term type) {
    var whnf = lhs.normalize(NormalizeMode.WHNF);
    var rhsWhnf = preRhs.normalize(NormalizeMode.WHNF);
    if (Objects.equals(whnf, lhs) && Objects.equals(rhsWhnf, preRhs)) return false;
    return compare(whnf, rhsWhnf, type);
  }

  public <T> T checkParam(Term.@NotNull Param l, Term.@NotNull Param r, @NotNull Term type, Supplier<T> fail, Supplier<T> success) {
    if (l.explicit() != r.explicit()) return fail.get();
    if (!compare(l.type(), r.type(), type)) return fail.get();
    varSubst.put(r.ref(), l.toTerm());
    varSubst.put(l.ref(), r.toTerm());
    var result = success.get();
    varSubst.remove(r.ref());
    varSubst.remove(l.ref());
    return result;
  }

  /**
   * @apiNote Do not forget to remove variables out of localCtx after done checking.
   */
  public <T> T checkParams(SeqLike<Term.@NotNull Param> l, SeqLike<Term.@NotNull Param> r, Supplier<T> fail, Supplier<T> success) {
    if (!l.sizeEquals(r)) return fail.get();
    if (l.isEmpty()) return success.get();
    return checkParam(l.first(), r.first(), FormTerm.Univ.OMEGA, fail, () ->
      checkParams(l.view().drop(1), r.view().drop(1), fail, success));
  }

  /**
   * @apiNote this ignores {@link Arg#explicit()}
   */
  public boolean visitArgs(SeqLike<Arg<Term>> l, SeqLike<Arg<Term>> r, SeqLike<Term.Param> params) {
    return visitLists(l.view().map(Arg::term), r.view().map(Arg::term), params);
  }

  private boolean visitLists(SeqLike<Term> l, SeqLike<Term> r, @NotNull SeqLike<Term.Param> types) {
    if (!l.sizeEquals(r)) return false;
    if (!r.sizeEquals(types)) return false;
    var typesSubst = types.view();
    for (int i = 0; i < l.size(); i++) {
      var li = l.get(i);
      var head = typesSubst.first();
      if (!compare(li, r.get(i), head.type())) return false;
      typesSubst = typesSubst.drop(1).map(type -> type.subst(head.ref(), li));
    }
    return true;
  }
}
