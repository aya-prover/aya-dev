// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck.unify;

import asia.kala.collection.mutable.MutableHashMap;
import asia.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Var;
import org.mzi.concrete.Expr;
import org.mzi.core.term.*;
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

  @Contract(pure = true) protected DefEq(@NotNull Ordering ord, LevelEqn.@NotNull Set equations) {
    this.ord = ord;
    this.equations = equations;
  }
}
