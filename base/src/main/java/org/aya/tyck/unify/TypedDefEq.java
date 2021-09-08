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

  boolean accept(@NotNull Term type, @NotNull Term lhs, @NotNull Term rhs) {
    traceEntrance(new Trace.UnifyT(lhs.freezeHoles(levelEqns), rhs.freezeHoles(levelEqns),
      pos, type.freezeHoles(levelEqns)));
    var ret = visit(type, lhs, rhs);
    traceExit(ret);
    return ret;
  }

  public void traceExit(boolean result) {
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

  public boolean visit(@NotNull Object o, @NotNull Term lhs, @NotNull Term rhs) {
    switch (o) {
      case RefTerm type -> {
        return termDefeq.compare(lhs, rhs) != null;
      }
      case FormTerm.Univ type -> {
        return termDefeq.compare(lhs, rhs) != null;
      }
      case ElimTerm.App type -> {
        return termDefeq.compare(lhs, rhs) != null;
      }
      case CallTerm.Fn type -> {
        return termDefeq.compare(lhs, rhs) != null;
      }
      case CallTerm.Data type -> {
        return termDefeq.compare(lhs, rhs) != null;
      }
      case CallTerm.@NotNull Prim type -> {
        return termDefeq.compare(lhs, rhs) != null;
      }
      case ElimTerm.Proj type -> {
        return termDefeq.compare(lhs, rhs) != null;
      }
      case CallTerm.Access type -> {
        return termDefeq.compare(lhs, rhs) != null;
      }
      case CallTerm.@NotNull Hole type -> {
        return termDefeq.compare(lhs, rhs) != null;
      }
      case CallTerm.Struct type -> {
        var fieldSigs = type.ref().core.fields;
        var paramSubst = type.ref().core.telescope().view().zip(type.args().view()).map(x ->
          Tuple2.of(x._1.ref(), x._2.term())).<Var, Term>toImmutableMap();
        var fieldSubst = new Substituter.TermSubst(MutableHashMap.of());
        for (var fieldSig : fieldSigs) {
          var dummyVars = fieldSig.selfTele.map(par ->
            new LocalVar(par.ref().name(), par.ref().definition()));
          var dummy = dummyVars.zip(fieldSig.selfTele).map(vpa ->
            new Arg<Term>(new RefTerm(vpa._1, vpa._2.type()), vpa._2.explicit()));
          var l = new CallTerm.Access(lhs, fieldSig.ref(), type.sortArgs(), type.args(), dummy);
          var r = new CallTerm.Access(rhs, fieldSig.ref(), type.sortArgs(), type.args(), dummy);
          fieldSubst.add(fieldSig.ref(), l);
          if (!compare(l, r, fieldSig.result().subst(paramSubst).subst(fieldSubst))) return false;
        }
        return true;
      }
      case IntroTerm.Lambda type ->
        throw new IllegalStateException("LamTerm can never be a type of any term");
      case CallTerm.Con type ->
        throw new IllegalStateException("ConCall can never be a type of any term");
      case IntroTerm.Tuple type ->
        throw new IllegalStateException("TupTerm can never be a type of any term");
      case IntroTerm.New newTerm ->
        throw new IllegalStateException("NewTerm can never be a type of any term");
      case ErrorTerm term -> {
        return true;
      }
      case FormTerm.Sigma type -> {
        var params = type.params().view();
        for (int i = 1, size = type.params().size(); i <= size; i++) {
          var l = new ElimTerm.Proj(lhs, i);
          var currentParam = params.first();
          if (!compare(l, new ElimTerm.Proj(rhs, i), currentParam.type())) return false;
          params = params.drop(1).map(x -> x.subst(currentParam.ref(), l));
        }
        return true;
      }
      case FormTerm.Pi type -> {
        var dummyVar = new LocalVar("dummy");
        var ty = type.param().type();
        var dummy = new RefTerm(dummyVar, ty);
        var dummyArg = new Arg<Term>(dummy, type.param().explicit());
        return compare(CallTerm.make(lhs, dummyArg), CallTerm.make(rhs, dummyArg), type.substBody(dummy));
      }
      default -> throw new IllegalStateException("Pattern mismatch in TypedDefEq");
    }
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
