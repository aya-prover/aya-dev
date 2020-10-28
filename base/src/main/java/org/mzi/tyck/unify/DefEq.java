// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck.unify;

import asia.kala.collection.Seq;
import asia.kala.collection.mutable.MutableHashMap;
import asia.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Var;
import org.mzi.api.util.NormalizeMode;
import org.mzi.concrete.Expr;
import org.mzi.core.term.*;
import org.mzi.generic.Arg;
import org.mzi.core.Tele;
import org.mzi.tyck.sort.LevelEqn;
import org.mzi.tyck.sort.Sort;
import org.mzi.util.Ordering;

/**
 * @author ice1000
 */
public abstract class DefEq implements Term.BiVisitor<@NotNull Term, @Nullable Term, @NotNull Boolean> {
  protected @NotNull Ordering ord;
  protected final @NotNull LevelEqn.Set equations;
  protected final @NotNull MutableMap<@NotNull Var, @NotNull Var> varSubst = new MutableHashMap<>();
  public Expr expr;

  public boolean compare(@NotNull Term lhs, @NotNull Term rhs, @Nullable Term type) {
    if (lhs == rhs) return true;
    return lhs.accept(this, rhs, type);
  }

  @Override
  public @NotNull Boolean visitDT(@NotNull DT lhs, @NotNull Term preRhs, @Nullable Term type) {
    if (!(preRhs instanceof DT rhs)) return false;
    var l = lhs.telescope().toBuffer();
    var r = rhs.telescope().toBuffer();
    if (!l.sizeEquals(r)) return false;
    for (int i = 0; i < l.size(); i++) {
      if (!compare(l.get(i).type(), r.get(i).type(), UnivTerm.OMEGA)) {
        for (int j = 0; j < i; j++) varSubst.remove(r.get(j).ref());
        return false;
      }
      varSubst.put(r.get(i).ref(), l.get(i).ref());
    }

    return true;
  }

  @Override
  public @NotNull Boolean visitRef(@NotNull RefTerm lhs, @NotNull Term preRhs, @Nullable Term type) {
    if (!(preRhs instanceof RefTerm rhs)) return false;
    var var2 = varSubst.getOrDefault(rhs.var(), rhs.var());
    return var2 == lhs.var();
  }

  @Override
  public @NotNull Boolean visitHole(@NotNull HoleTerm lhs, @NotNull Term preRhs, @Nullable Term type) {
    if (lhs.solution().isDefined()) return lhs.solution().get().accept(this, preRhs, type);
    return preRhs instanceof HoleTerm rhs && lhs.var() == rhs.var();
  }

  @Override
  public @NotNull Boolean visitProj(@NotNull ProjTerm lhs, @NotNull Term preRhs, @Nullable Term type) {
    return preRhs instanceof ProjTerm rhs && lhs.ix() == rhs.ix() && compare(lhs, rhs, null);
  }

  @Override
  public @NotNull Boolean visitUniv(@NotNull UnivTerm lhs, @NotNull Term preRhs, @Nullable Term type) {
    if (!(preRhs instanceof UnivTerm rhs)) return false;
    return Sort.compare(lhs.sort(), rhs.sort(), ord, equations, expr);
  }

  @Override
  public @NotNull Boolean visitTup(@NotNull TupTerm lhs, @NotNull Term preRhs, @Nullable Term type) {
    if (!(preRhs instanceof TupTerm rhs)) return false;
    // TODO[ice]: eta-rule
    // TODO[ice]: make type-directed
    return visitLists(lhs.items(), rhs.items());
  }

  private boolean visitLists(Seq<Term> l, Seq<Term> r) {
    if (!l.sizeEquals(r)) return false;
    for (int i = 0; i < l.size(); i++) if (!compare(l.get(i), r.get(i), null)) return false;
    return true;
  }

  @Override
  public @NotNull Boolean visitFnCall(@NotNull AppTerm.FnCall lhs, @NotNull Term preRhs, @Nullable Term type) {
    if (preRhs instanceof AppTerm.FnCall rhs && rhs.fnRef() == lhs.fnRef())
      return visitLists(lhs.args().map(Arg::term), rhs.args().map(Arg::term));
    return lhs.normalize(NormalizeMode.WHNF).accept(this, preRhs, type);
  }

  @Override
  public @NotNull Boolean visitLam(@NotNull LamTerm lhs, @NotNull Term preRhs, @Nullable Term type) {
    if (!(preRhs instanceof LamTerm rhs)) return false;
    var params = Tele.biForEach(lhs.tele(), rhs.tele(), (l, r) -> varSubst.put(l.ref(), r.ref()));
    var lBody = lhs.body();
    var rBody = rhs.body();
    var lTele = params._1;
    while (lTele != null) {
      lBody = AppTerm.make(lBody, new Arg<>(new RefTerm(lTele.ref()), lTele.explicit()));
      lTele = lTele.next();
    }
    var rTele = params._2;
    while (rTele != null) {
      rBody = AppTerm.make(rBody, new Arg<>(new RefTerm(rTele.ref()), rTele.explicit()));
      rTele = rTele.next();
    }
    // TODO[ice]: maybe we can optimize this computation? We've already traversed lhs.tele and rhs.tele
    if (type != null) type = type.dropTele(Math.max(lhs.tele().size(), rhs.tele().size()));
    var result = compare(rBody, lBody, type);
    Tele.biForEach(lhs.tele(), rhs.tele(), (l, r) -> varSubst.remove(l.ref()));
    return result;
  }

  @Contract(pure = true) protected DefEq(@NotNull Ordering ord, LevelEqn.@NotNull Set equations) {
    this.ord = ord;
    this.equations = equations;
  }
}
