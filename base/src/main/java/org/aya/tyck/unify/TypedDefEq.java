// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.unify;

import org.aya.api.error.SourcePos;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.Arg;
import org.aya.api.util.NormalizeMode;
import org.aya.core.term.*;
import org.aya.tyck.LocalCtx;
import org.aya.tyck.trace.Trace;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author ice1000
 * @implNote Do not call <code>expr.accept(this, bla)</code> directly.
 * Instead, invoke {@link TypedDefEq#compare(Term, Term, Term)} to do so.
 */
public final class TypedDefEq implements Term.BiVisitor<@NotNull Term, @NotNull Term, @NotNull Boolean> {
  protected final @NotNull MutableMap<@NotNull LocalVar, @NotNull LocalVar> varSubst = new MutableHashMap<>();
  public final @NotNull LocalCtx localCtx;
  private final @NotNull PatDefEq termDirectedDefeq;
  public final Trace.@Nullable Builder traceBuilder;
  public final @NotNull SourcePos pos;

  private void tracing(@NotNull Consumer<Trace.@NotNull Builder> consumer) {
    if (traceBuilder != null) consumer.accept(traceBuilder);
  }

  void traceEntrance(@NotNull Trace trace) {
    tracing(builder -> builder.shift(trace));
  }

  @Override public void traceEntrance(@NotNull Term type, @NotNull Term lhs, @NotNull Term rhs) {
    traceEntrance(new Trace.UnifyT(lhs, rhs, pos, type));
  }

  @Override public void traceExit(@NotNull Boolean result) {
    tracing(Trace.Builder::reduce);
  }

  public TypedDefEq(
    @NotNull Function<@NotNull TypedDefEq, @NotNull PatDefEq> createTypedDefEq,
    @NotNull LocalCtx localCtx, Trace.@Nullable Builder traceBuilder, @NotNull SourcePos pos
  ) {
    this.localCtx = localCtx;
    this.termDirectedDefeq = createTypedDefEq.apply(this);
    this.traceBuilder = traceBuilder;
    this.pos = pos;
  }

  public boolean compare(@NotNull Term lhs, @NotNull Term rhs, @NotNull Term type) {
    if (lhs == rhs) return true;
    type = type.normalize(NormalizeMode.WHNF);
    // at least one of them is not an FnCall
    if (isNotCall(lhs) || isNotCall(rhs)) {
      lhs = lhs.normalize(NormalizeMode.WHNF);
      rhs = rhs.normalize(NormalizeMode.WHNF);
    }
    if (rhs instanceof CallTerm.Hole) return type.accept(this, rhs, lhs);
    return type.accept(this, lhs, rhs);
  }

  private boolean isNotCall(@NotNull Term term) {
    return !(term instanceof CallTerm.Fn || term instanceof CallTerm.Con);
  }

  public boolean compareWHNF(Term lhs, Term preRhs, @NotNull Term type) {
    var whnf = lhs.normalize(NormalizeMode.WHNF);
    var rhsWhnf = preRhs.normalize(NormalizeMode.WHNF);
    if (Objects.equals(whnf, lhs) && Objects.equals(rhsWhnf, preRhs)) return false;
    return compare(whnf, rhsWhnf, type);
  }

  public <T> T checkParam(Term.@NotNull Param l, Term.@NotNull Param r, Supplier<T> fail, Supplier<T> success) {
    if (l.explicit() != r.explicit()) return fail.get();
    if (!compare(l.type(), r.type(), FormTerm.Univ.OMEGA)) return fail.get();
    varSubst.put(r.ref(), l.ref());
    varSubst.put(l.ref(), r.ref());
    var result = localCtx.with(l, () -> localCtx.with(r, success));
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
    return checkParam(l.first(), r.first(), fail, () ->
      checkParams(l.view().drop(1), r.view().drop(1), fail, success));
  }

  @Override public @NotNull Boolean visitRef(@NotNull RefTerm type, @NotNull Term lhs, @NotNull Term rhs) {
    return termDirectedDefeq.compare(lhs, rhs, type);
  }

  @Override public @NotNull Boolean visitLam(@NotNull IntroTerm.Lambda type, @NotNull Term lhs, @NotNull Term rhs) {
    throw new IllegalStateException("LamTerm can never be a type of any term");
  }

  @Override public @NotNull Boolean visitUniv(@NotNull FormTerm.Univ type, @NotNull Term lhs, @NotNull Term rhs) {
    return termDirectedDefeq.compare(lhs, rhs, type);
  }

  @Override public @NotNull Boolean visitApp(@NotNull ElimTerm.App type, @NotNull Term lhs, @NotNull Term rhs) {
    return termDirectedDefeq.compare(lhs, rhs, type);
  }

  @Override public @NotNull Boolean visitFnCall(@NotNull CallTerm.Fn type, @NotNull Term lhs, @NotNull Term rhs) {
    return termDirectedDefeq.compare(lhs, rhs, type);
  }

  @Override public @NotNull Boolean visitDataCall(@NotNull CallTerm.Data type, @NotNull Term lhs, @NotNull Term rhs) {
    return termDirectedDefeq.compare(lhs, rhs, type);
  }

  @Override
  public @NotNull Boolean visitStructCall(@NotNull CallTerm.Struct type, @NotNull Term lhs, @NotNull Term rhs) {
    return termDirectedDefeq.compare(lhs, rhs, type);
  }

  @Override public @NotNull Boolean visitPrimCall(CallTerm.@NotNull Prim type, @NotNull Term lhs, @NotNull Term rhs) {
    return termDirectedDefeq.compare(lhs, rhs, type);
  }

  public @NotNull Boolean visitConCall(@NotNull CallTerm.Con type, @NotNull Term lhs, @NotNull Term rhs) {
    throw new IllegalStateException("ConCall can never be a type of any term");
  }

  @Override
  public @NotNull Boolean visitTup(@NotNull IntroTerm.Tuple type, @NotNull Term lhs, @NotNull Term rhs) {
    throw new IllegalStateException("TupTerm can never be a type of any term");
  }

  @Override
  public @NotNull Boolean visitNew(@NotNull IntroTerm.New newTerm, @NotNull Term term, @NotNull Term term2) {
    throw new IllegalStateException("NewTerm can never be a type of any term");
  }

  @Override
  public @NotNull Boolean visitProj(@NotNull ElimTerm.Proj type, @NotNull Term lhs, @NotNull Term rhs) {
    return termDirectedDefeq.compare(lhs, rhs, type);
  }

  @Override
  public @NotNull Boolean visitAccess(@NotNull CallTerm.Access type, @NotNull Term lhs, @NotNull Term rhs) {
    return termDirectedDefeq.compare(lhs, rhs, type);
  }

  @Override
  public @NotNull Boolean visitHole(CallTerm.@NotNull Hole type, @NotNull Term lhs, @NotNull Term rhs) {
    return termDirectedDefeq.compare(lhs, rhs, type);
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

  @Override
  public @NotNull Boolean visitPi(@NotNull FormTerm.Pi type, @NotNull Term lhs, @NotNull Term rhs) {
    var dummyVar = new LocalVar("dummy");
    var dummy = new RefTerm(dummyVar);
    var dummyArg = new Arg<Term>(dummy, type.param().explicit());
    return localCtx.with(dummyVar, type.param().type(), () ->
      compare(CallTerm.make(lhs, dummyArg), CallTerm.make(rhs, dummyArg), type.substBody(dummy)));
  }

  @Override
  public @NotNull Boolean visitSigma(@NotNull FormTerm.Sigma type, @NotNull Term lhs, @NotNull Term rhs) {
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
