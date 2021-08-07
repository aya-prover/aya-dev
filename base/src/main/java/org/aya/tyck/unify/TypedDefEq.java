// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.unify;

import kala.collection.SeqLike;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple2;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.api.util.Arg;
import org.aya.api.util.NormalizeMode;
import org.aya.core.term.*;
import org.aya.core.visitor.Substituter;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.trace.Trace;
import org.aya.util.Ordering;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author ice1000
 * @implNote Do not call <code>expr.accept(this, bla)</code> directly.
 * Instead, invoke {@link TypedDefEq#compare(Term, Term, Term)} to do so.
 */
public final class TypedDefEq implements Term.BiVisitor<@NotNull Term, @NotNull Term, @NotNull Boolean> {
  final @NotNull MutableMap<@NotNull LocalVar, @NotNull RefTerm> varSubst = new MutableHashMap<>();
  private final @NotNull UntypedDefEq termDefeq;
  public final @NotNull ExprTycker tycker;
  public final @NotNull SourcePos pos;

  void tracing(@NotNull Consumer<Trace.@NotNull Builder> consumer) {
    if (tycker.traceBuilder != null) consumer.accept(tycker.traceBuilder);
  }

  void traceEntrance(@NotNull Trace trace) {
    tracing(builder -> builder.shift(trace));
  }

  @Override public void traceEntrance(@NotNull Term type, @NotNull Term lhs, @NotNull Term rhs) {
    traceEntrance(new Trace.UnifyT(lhs.freezeHoles(), rhs.freezeHoles(), pos, type.freezeHoles()));
  }

  @Override public void traceExit(@NotNull Boolean result) {
    tracing(Trace.Builder::reduce);
  }

  public TypedDefEq(@NotNull Ordering cmp, @NotNull ExprTycker tycker, @NotNull SourcePos pos) {
    this.tycker = tycker;
    this.pos = pos;
    this.termDefeq = new UntypedDefEq(this, cmp);
  }

  public boolean compare(@NotNull Term lhs, @NotNull Term rhs, @NotNull Term type) {
    if (lhs == rhs) return true;
    type = type.normalize(NormalizeMode.WHNF);
    // at least one of them is not an FnCall
    if (!isCall(lhs) || !isCall(rhs)) {
      lhs = lhs.normalize(NormalizeMode.WHNF);
      rhs = rhs.normalize(NormalizeMode.WHNF);
    }
    if (rhs instanceof CallTerm.Hole) return type.accept(this, rhs, lhs);
    return type.accept(this, lhs, rhs);
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

  @Override public @NotNull Boolean visitRef(@NotNull RefTerm type, @NotNull Term lhs, @NotNull Term rhs) {
    return termDefeq.compare(lhs, rhs) != null;
  }

  @Override public @NotNull Boolean visitLam(@NotNull IntroTerm.Lambda type, @NotNull Term lhs, @NotNull Term rhs) {
    throw new IllegalStateException("LamTerm can never be a type of any term");
  }

  @Override public @NotNull Boolean visitUniv(@NotNull FormTerm.Univ type, @NotNull Term lhs, @NotNull Term rhs) {
    return termDefeq.compare(lhs, rhs) != null;
  }

  @Override public @NotNull Boolean visitApp(@NotNull ElimTerm.App type, @NotNull Term lhs, @NotNull Term rhs) {
    return termDefeq.compare(lhs, rhs) != null;
  }

  @Override public @NotNull Boolean visitFnCall(@NotNull CallTerm.Fn type, @NotNull Term lhs, @NotNull Term rhs) {
    return termDefeq.compare(lhs, rhs) != null;
  }

  @Override public @NotNull Boolean visitDataCall(@NotNull CallTerm.Data type, @NotNull Term lhs, @NotNull Term rhs) {
    return termDefeq.compare(lhs, rhs) != null;
  }

  @Override
  public @NotNull Boolean visitStructCall(@NotNull CallTerm.Struct type, @NotNull Term lhs, @NotNull Term rhs) {
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

  @Override public @NotNull Boolean visitPrimCall(CallTerm.@NotNull Prim type, @NotNull Term lhs, @NotNull Term rhs) {
    return termDefeq.compare(lhs, rhs) != null;
  }

  public @NotNull Boolean visitConCall(@NotNull CallTerm.Con type, @NotNull Term lhs, @NotNull Term rhs) {
    throw new IllegalStateException("ConCall can never be a type of any term");
  }

  @Override public @NotNull Boolean visitTup(@NotNull IntroTerm.Tuple type, @NotNull Term lhs, @NotNull Term rhs) {
    throw new IllegalStateException("TupTerm can never be a type of any term");
  }

  @Override public @NotNull Boolean visitNew(@NotNull IntroTerm.New newTerm, @NotNull Term term, @NotNull Term term2) {
    throw new IllegalStateException("NewTerm can never be a type of any term");
  }

  @Override public @NotNull Boolean visitProj(@NotNull ElimTerm.Proj type, @NotNull Term lhs, @NotNull Term rhs) {
    return termDefeq.compare(lhs, rhs) != null;
  }

  @Override public @NotNull Boolean visitAccess(@NotNull CallTerm.Access type, @NotNull Term lhs, @NotNull Term rhs) {
    return termDefeq.compare(lhs, rhs) != null;
  }

  @Override public @NotNull Boolean visitHole(CallTerm.@NotNull Hole type, @NotNull Term lhs, @NotNull Term rhs) {
    return termDefeq.compare(lhs, rhs) != null;
  }

  @Override public @NotNull Boolean visitError(@NotNull ErrorTerm term, @NotNull Term term2, @NotNull Term term3) {
    return true;
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

  @Override public @NotNull Boolean visitPi(@NotNull FormTerm.Pi type, @NotNull Term lhs, @NotNull Term rhs) {
    var dummyVar = new LocalVar("dummy");
    var ty = type.param().type();
    var dummy = new RefTerm(dummyVar, ty);
    var dummyArg = new Arg<Term>(dummy, type.param().explicit());
    return compare(CallTerm.make(lhs, dummyArg), CallTerm.make(rhs, dummyArg), type.substBody(dummy));
  }

  @Override public @NotNull Boolean visitSigma(@NotNull FormTerm.Sigma type, @NotNull Term lhs, @NotNull Term rhs) {
    var params = type.params().view();
    for (int i = 1, size = type.params().size(); i <= size; i++) {
      var l = new ElimTerm.Proj(lhs, i);
      var currentParam = params.first();
      if (!compare(l, new ElimTerm.Proj(rhs, i), currentParam.type())) return false;
      params = params.drop(1).map(x -> x.subst(currentParam.ref(), l));
    }
    return true;
  }
}
