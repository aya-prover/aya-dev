// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck.unify;

import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Var;
import org.mzi.api.util.NormalizeMode;
import org.mzi.concrete.Expr;
import org.mzi.core.Param;
import org.mzi.core.term.*;
import org.mzi.generic.Arg;
import org.mzi.tyck.sort.LevelEqn;
import org.mzi.tyck.sort.Sort;
import org.mzi.util.Decision;
import org.mzi.util.Ordering;

import java.util.Objects;
import java.util.stream.IntStream;

/**
 * @author ice1000
 * @implNote Do not call <code>expr.accept(this, bla)</code> directly.
 * Instead, invoke {@link DefEq#compare(Term, Term, Term)} to do so.
 */
public abstract class DefEq implements Term.BiVisitor<@NotNull Term, @Nullable Term, @NotNull Boolean> {
  protected @NotNull Ordering ord;
  protected final @NotNull LevelEqn.Set equations;
  protected final @NotNull MutableMap<@NotNull Var, @NotNull Var> varSubst = new MutableHashMap<>();
  public Expr expr;

  public boolean compare(@NotNull Term lhs, @NotNull Term rhs, @Nullable Term type) {
    if (lhs == rhs) return true;
    // One of 'em clearly not WHNF but the other is or may be
    if ((lhs.whnf() == Decision.NO) == (rhs.whnf() != Decision.NO)) {
      lhs = lhs.normalize(NormalizeMode.WHNF);
      rhs = rhs.normalize(NormalizeMode.WHNF);
    }
    return lhs.accept(this, rhs, type);
  }

  @NotNull
  private Boolean checkParam(@NotNull Param l, @NotNull Param r) {
    if (!compare(l.type(), r.type(), UnivTerm.OMEGA)) return false;
    varSubst.put(l.ref(), r.ref());
    return true;
  }

  @NotNull
  private Boolean checkParams(ImmutableSeq<@NotNull Param> l, ImmutableSeq<@NotNull Param> r) {
    if (!l.sizeEquals(r)) return false;
    var length = l.size();
    for (int i = 0; i < length; i++) {
      if (!compare(l.get(i).type(), r.get(i).type(), UnivTerm.OMEGA)) {
        for (int j = 0; j < i; j++) varSubst.remove(r.get(j).ref());
        return false;
      }
      varSubst.put(r.get(i).ref(), l.get(i).ref());
    }
    return true;
  }

  @Override
  public @NotNull Boolean visitPi(@NotNull PiTerm lhs, @NotNull Term preRhs, @Nullable Term type) {
      return preRhs instanceof PiTerm rhs
          && lhs.co() == rhs.co()
          && checkParam(lhs.param(), rhs.param())
          && compare(lhs.body(), rhs.body(), type);
  }

  @Override
  public @NotNull Boolean visitSigma(@NotNull SigmaTerm lhs, @NotNull Term preRhs, @Nullable Term type) {
    return preRhs instanceof SigmaTerm rhs
      && lhs.co() == rhs.co()
      && checkParams(lhs.params(), rhs.params())
      && compare(lhs.body(), rhs.body(), type);
  }

  @Override
  public @NotNull Boolean visitRef(@NotNull RefTerm lhs, @NotNull Term preRhs, @Nullable Term type) {
    if (!(preRhs instanceof RefTerm rhs)) {
      ord = ord.invert();
      var result = compare(preRhs, lhs, type);
      ord = ord.invert();
      return result;
    }
    return varSubst.getOrDefault(rhs.var(), rhs.var()) == lhs.var();
  }

  @Override
  public @NotNull Boolean visitApp(@NotNull AppTerm.Apply lhs, @NotNull Term preRhs, @Nullable Term type) {
    if (lhs.whnf() == Decision.YES && preRhs instanceof AppTerm.Apply rhs)
      return compare(lhs.fn(), rhs.fn(), null) && compare(lhs.arg().term(), rhs.arg().term(), null);
    var lhsWhnf = lhs.normalize(NormalizeMode.WHNF);
    if (Objects.equals(lhsWhnf, lhs)) return false;
    return compare(lhsWhnf, preRhs.normalize(NormalizeMode.WHNF), type);
  }

  @Override
  public @NotNull Boolean visitProj(@NotNull ProjTerm lhs, @NotNull Term preRhs, @Nullable Term type) {
    if (lhs.whnf() == Decision.YES && preRhs instanceof ProjTerm rhs)
      return lhs.ix() == rhs.ix() && compare(lhs.tup(), rhs.tup(), null);
    var whnf = lhs.normalize(NormalizeMode.WHNF);
    if (Objects.equals(whnf, lhs)) return false;
    return compare(whnf, preRhs.normalize(NormalizeMode.WHNF), type);
  }

  @Override
  public @NotNull Boolean visitUniv(@NotNull UnivTerm lhs, @NotNull Term preRhs, @Nullable Term type) {
    return preRhs instanceof UnivTerm rhs
        && Sort.compare(lhs.sort(), rhs.sort(), ord, equations, expr);
  }

  @Override
  public @NotNull Boolean visitTup(@NotNull TupTerm lhs, @NotNull Term preRhs, @Nullable Term type) {
    if (!(preRhs instanceof TupTerm rhs)) {
      if (!(type instanceof SigmaTerm sigma)) return false;
      // Eta-rule
      var tupRhs = LinkedBuffer.<Term>of();
      for (int i = lhs.items().size(); i > 0; i--) {
        tupRhs.push(new ProjTerm(preRhs, i));
      }
      return visitLists(lhs.items(), tupRhs,
        sigma.params().view().map(Param::type).appended(sigma.body()));
    }
    return visitLists(lhs.items(), rhs.items());
  }

  private boolean visitLists(Seq<? extends Term> l, Seq<? extends Term> r) {
    if (!l.sizeEquals(r)) return false;
    return IntStream.range(0, l.size()).allMatch(i -> compare(l.get(i), r.get(i), null));
  }

  private boolean visitLists(Seq<? extends Term> l, Seq<? extends Term> r, @NotNull Seq<? extends Term> types) {
    if (!l.sizeEquals(r)) return false;
    if (!r.sizeEquals(types)) return false;
    for (int i = 0; i < l.size(); i++) {
      if (!compare(l.get(i), r.get(i), types.get(i))) return false;
    }
    return true;
  }

  @Override
  public @NotNull Boolean visitFnCall(@NotNull AppTerm.FnCall lhs, @NotNull Term preRhs, @Nullable Term type) {
    if (preRhs instanceof AppTerm.FnCall rhs && rhs.fnRef() == lhs.fnRef())
      if (visitLists(lhs.args().view().map(Arg::term), rhs.args().view().map(Arg::term)))
        return true;
    return compare(lhs.normalize(NormalizeMode.WHNF), preRhs.normalize(NormalizeMode.WHNF), type);
  }

  @Override
  public @NotNull Boolean visitLam(@NotNull LamTerm lhs, @NotNull Term preRhs, @Nullable Term type) {
    if (!(preRhs instanceof LamTerm rhs)) {
      var exParam = lhs.param();
      var exArg = new Arg<>(new RefTerm(exParam.ref()), exParam.explicit());
      return compare(AppTerm.make(lhs, exArg), AppTerm.make(preRhs, exArg), type);
    }
    return checkParam(lhs.param(), rhs.param())
      && compare(lhs.body(), rhs.body(), type);
  }

  @Contract(pure = true) protected DefEq(@NotNull Ordering ord, LevelEqn.@NotNull Set equations) {
    this.ord = ord;
    this.equations = equations;
  }
}
