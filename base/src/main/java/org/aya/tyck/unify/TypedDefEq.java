// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.unify;

import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.api.util.NormalizeMode;
import org.aya.core.term.*;
import org.aya.generic.Arg;
import org.aya.ref.LocalVar;
import org.aya.tyck.trace.Trace;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author ice1000
 * @implNote Do not call <code>expr.accept(this, bla)</code> directly.
 * Instead, invoke {@link TypedDefEq#compare(Term, Term, Term)} to do so.
 */
public final class TypedDefEq implements Term.BiVisitor<@NotNull Term, @NotNull Term, @NotNull Boolean> {
  protected final @NotNull MutableMap<@NotNull Var, @NotNull Var> varSubst = new MutableHashMap<>();
  public final @NotNull MutableMap<Var, Term> localCtx;
  private final @NotNull PatDefEq termDirectedDefeq;
  public Trace.@Nullable Builder traceBuilder = null;
  public final @NotNull SourcePos pos;

  private void tracing(@NotNull Consumer<Trace.@NotNull Builder> consumer) {
    if (traceBuilder != null) consumer.accept(traceBuilder);
  }

  void traceEntrance(@NotNull Trace trace) {
    tracing(builder -> builder.shift(trace));
  }

  @Override
  public void traceEntrance(@NotNull Term type, @NotNull Term lhs, @NotNull Term rhs) {
    traceEntrance(new Trace.UnifyT(lhs, rhs, pos));
  }

  @Override
  public void traceExit(@NotNull Boolean result) {
    tracing(Trace.Builder::reduce);
  }

  public TypedDefEq(
    @NotNull Function<@NotNull TypedDefEq, @NotNull PatDefEq> createTypedDefEq,
    @NotNull MutableMap<Var, Term> localCtx,
    @NotNull SourcePos pos
  ) {
    this.localCtx = localCtx;
    this.termDirectedDefeq = createTypedDefEq.apply(this);
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
    if (rhs instanceof AppTerm.HoleApp) return type.accept(this, rhs, lhs);
    return type.accept(this, lhs, rhs);
  }

  private boolean isNotCall(@NotNull Term term) {
    return !(term instanceof AppTerm.FnCall);
  }

  public boolean compareWHNF(Term lhs, Term preRhs, @NotNull Term type) {
    var whnf = lhs.normalize(NormalizeMode.WHNF);
    if (Objects.equals(whnf, lhs)) return false;
    return compare(whnf, preRhs.normalize(NormalizeMode.WHNF), type);
  }

  @NotNull
  public Boolean checkParam(Term.@NotNull Param l, Term.@NotNull Param r) {
    if (l.explicit() != r.explicit()) return false;
    if (!compare(l.type(), r.type(), UnivTerm.OMEGA)) return false;
    varSubst.put(r.ref(), l.ref());
    localCtx.put(l.ref(), l.type());
    return true;
  }

  /**
   * @apiNote Do not forget to remove variables out of localCtx after done checking.
   */
  @NotNull
  public Boolean checkParams(ImmutableSeq<Term.@NotNull Param> l, ImmutableSeq<Term.@NotNull Param> r) {
    if (!l.sizeEquals(r)) return false;
    var length = l.size();
    for (int i = 0; i < length; i++) {
      final var rhs = r.get(i);
      final var lhs = l.get(i);
      if (!compare(lhs.type(), rhs.type(), UnivTerm.OMEGA) || lhs.explicit() != rhs.explicit()) {
        for (int j = 0; j < i; j++) {
          varSubst.remove(r.get(j).ref());
          localCtx.remove(lhs.ref());
          localCtx.remove(rhs.ref());
        }
        return false;
      }
      varSubst.put(rhs.ref(), lhs.ref());
      localCtx.put(lhs.ref(), lhs.type());
      localCtx.put(rhs.ref(), rhs.type());
    }
    return true;
  }

  @Override
  public @NotNull Boolean visitRef(@NotNull RefTerm type, @NotNull Term lhs, @NotNull Term rhs) {
    return termDirectedDefeq.compare(lhs, rhs, type);
  }

  @Override
  public @NotNull Boolean visitLam(@NotNull LamTerm type, @NotNull Term lhs, @NotNull Term rhs) {
    throw new IllegalStateException("LamTerm can never be a type of any term");
  }

  @Override
  public @NotNull Boolean visitUniv(@NotNull UnivTerm type, @NotNull Term lhs, @NotNull Term rhs) {
    return termDirectedDefeq.compare(lhs, rhs, type);
  }

  @Override
  public @NotNull Boolean visitApp(@NotNull AppTerm.Apply type, @NotNull Term lhs, @NotNull Term rhs) {
    return termDirectedDefeq.compare(lhs, rhs, type);
  }

  @Override
  public @NotNull Boolean visitFnCall(@NotNull AppTerm.FnCall type, @NotNull Term lhs, @NotNull Term rhs) {
    return termDirectedDefeq.compare(lhs, rhs, type);
  }

  @Override
  public @NotNull Boolean visitDataCall(@NotNull AppTerm.DataCall type, @NotNull Term lhs, @NotNull Term rhs) {
    return termDirectedDefeq.compare(lhs, rhs, type);
  }

  @Override
  public @NotNull Boolean visitConCall(@NotNull AppTerm.ConCall type, @NotNull Term lhs, @NotNull Term rhs) {
    throw new IllegalStateException("ConCall can never be a type of any term");
  }

  @Override
  public @NotNull Boolean visitTup(@NotNull TupTerm type, @NotNull Term lhs, @NotNull Term rhs) {
    throw new IllegalStateException("TupTerm can never be a type of any term");
  }

  @Override
  public @NotNull Boolean visitProj(@NotNull ProjTerm type, @NotNull Term lhs, @NotNull Term rhs) {
    return termDirectedDefeq.compare(lhs, rhs, type);
  }

  @Override
  public @NotNull Boolean visitHole(AppTerm.@NotNull HoleApp type, @NotNull Term lhs, @NotNull Term rhs) {
    return termDirectedDefeq.compare(lhs, rhs, type);
  }

  /**
   * @apiNote this ignores {@link Arg#explicit()}
   */
  public boolean visitArgs(SeqLike<? extends Arg<? extends Term>> l, SeqLike<? extends Arg<? extends Term>> r, SeqLike<? extends Term.Param> params) {
    return visitLists(l.view().map(Arg::term), r.view().map(Arg::term), params);
  }

  private boolean visitLists(SeqLike<? extends Term> l, SeqLike<? extends Term> r, @NotNull SeqLike<? extends Term.Param> types) {
    if (!l.sizeEquals(r)) return false;
    if (!r.sizeEquals(types)) return false;
    var typesSubstituted = types;
    for (int i = 0; i < l.size(); i++) {
      var li = l.get(i);
      if (!compare(li, r.get(i), typesSubstituted.first().type())) return false;
      typesSubstituted = types.view().drop(1).map(type -> type.subst(types.first().ref(), li));
    }
    return true;
  }

  @Override
  public @NotNull Boolean visitPi(@NotNull PiTerm type, @NotNull Term lhs, @NotNull Term rhs) {
    var dummy = new RefTerm(new LocalVar("dummy"));
    var dummyArg = new Arg<Term>(dummy, type.param().explicit());
    localCtx.put(dummy.var(), type.param().type());
    var result = compare(AppTerm.make(lhs, dummyArg), AppTerm.make(rhs, dummyArg), type.body().subst(type.param().ref(), dummy));
    localCtx.remove(dummy.var());
    return result;
  }

  @Override
  public @NotNull Boolean visitSigma(@NotNull SigmaTerm type, @NotNull Term lhs, @NotNull Term rhs) {
    var params = type.params();
    var body = type.body();
    for (int i = 1; i <= type.params().size(); i++) {
      var l = new ProjTerm(lhs, i);
      var currentParam = params.first();
      if (!compare(l, new ProjTerm(rhs, i), currentParam.type())) return false;
      params = params.drop(1).map(x -> x.subst(currentParam.ref(), l));
      body = body.subst(currentParam.ref(), l);
    }
    var len = type.params().size() + 1;
    return compare(new ProjTerm(lhs, len), new ProjTerm(rhs, len), body);
  }
}
