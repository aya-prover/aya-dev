// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.tyck.unify;

import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.concrete.Expr;
import org.mzi.core.term.*;
import org.mzi.core.visitor.Substituter;
import org.mzi.generic.Arg;
import org.mzi.ref.LocalVar;
import org.mzi.tyck.MetaContext;
import org.mzi.tyck.error.HoleBadSpineWarn;
import org.mzi.util.Decision;
import org.mzi.util.Ordering;

/**
 * The implementation of untyped pattern unification for holes.
 * András Kovács' elaboration-zoo is taken as reference.
 *
 * @author re-xyr, ice1000
 */
public final class PatDefEq implements Term.BiVisitor<@NotNull Term, @NotNull Term, @NotNull Boolean> {
  private final @NotNull TypedDefEq defeq;
  private final @NotNull UntypedDefEq untypedDefeq;

  protected @NotNull Ordering ord;
  protected @NotNull MetaContext metaContext;
  protected Expr expr;

  public boolean compare(@NotNull Term lhs, @NotNull Term rhs, @NotNull Term type) {
    return lhs.accept(this, rhs, type);
  }

  @Override
  public @NotNull Boolean visitRef(@NotNull RefTerm lhs, @NotNull Term preRhs, @NotNull Term type) {
    return passDown(lhs, preRhs, type);
  }

  @Override
  public @NotNull Boolean visitLam(@NotNull LamTerm lhs, @NotNull Term preRhs, @NotNull Term type) {
    throw new IllegalStateException("No visitLam in PatDefEq");
  }

  @Override
  public @NotNull Boolean visitPi(@NotNull PiTerm lhs, @NotNull Term preRhs, @NotNull Term type) {
    return passDown(lhs, preRhs, type);
  }

  @Override
  public @NotNull Boolean visitSigma(@NotNull SigmaTerm lhs, @NotNull Term preRhs, @NotNull Term type) {
    return passDown(lhs, preRhs, type);
  }

  @Override
  public @NotNull Boolean visitUniv(@NotNull UnivTerm lhs, @NotNull Term preRhs, @NotNull Term type) {
    return passDown(lhs, preRhs, type);
  }

  @Override
  public @NotNull Boolean visitApp(@NotNull AppTerm.Apply lhs, @NotNull Term preRhs, @NotNull Term type) {
    return passDown(lhs, preRhs, type);
  }

  @Override
  public @NotNull Boolean visitFnCall(@NotNull AppTerm.FnCall lhs, @NotNull Term preRhs, @NotNull Term type) {
    if (!(preRhs instanceof AppTerm.FnCall rhs)
      || lhs.fnRef() != rhs.fnRef()) {
      if (lhs.whnf() != Decision.NO) return false;
      return defeq.compareWHNF(lhs, preRhs, type);
    }
    return defeq.visitArgs(lhs.args(), rhs.args(), lhs.fnRef().core.telescope());
  }

  @Override
  public @NotNull Boolean visitDataCall(@NotNull AppTerm.DataCall lhs, @NotNull Term preRhs, @NotNull Term type) {
    if (!(preRhs instanceof AppTerm.DataCall rhs)
      || lhs.dataRef() != rhs.dataRef())
      return false;
    return defeq.visitArgs(lhs.args(), rhs.args(), lhs.dataRef().core.telescope());
  }

  @Override
  public @NotNull Boolean visitTup(@NotNull TupTerm lhs, @NotNull Term preRhs, @NotNull Term type) {
    throw new IllegalStateException("No visitTup in TermDirectedDefEq");
  }

  @Override
  public @NotNull Boolean visitProj(@NotNull ProjTerm lhs, @NotNull Term preRhs, @NotNull Term type) {
    return passDown(lhs, preRhs, type);
  }

  private @NotNull Boolean passDown(@NotNull Term lhs, @NotNull Term preRhs, @NotNull Term type) {
    var inferred = untypedDefeq.compare(lhs, preRhs);
    if (inferred == null) return false;
    return defeq.compare(inferred, type, UnivTerm.OMEGA); // TODO[xyr]: proper subtyping?
  }

  public PatDefEq(@NotNull TypedDefEq defeq, @NotNull Ordering ord, @NotNull MetaContext metaContext) {
    this.defeq = defeq;
    this.untypedDefeq = new UntypedDefEq(defeq);
    this.ord = ord;
    this.metaContext = metaContext;
  }

  private @Nullable Term extract(Seq<? extends Arg<? extends Term>> spine, Term rhs) {
    var subst = new Substituter.TermSubst(new MutableHashMap<>(/*spine.size() * 2*/));
    for (var arg : spine.view()) {
      if (arg.term() instanceof RefTerm ref && ref.var() instanceof LocalVar var) {
        rhs = extractVar(rhs, subst, arg, var);
        if (rhs == null) return null;
      } else return null;
      // TODO[ice]: ^ eta var
    }
    return rhs.subst(subst);
  }

  private @Nullable Term extractVar(Term rhs, Substituter.TermSubst subst, Arg<? extends Term> arg, LocalVar var) {
    if (subst.map().containsKey(var)) {
      // TODO[ice]: report errors for duplicated vars in spine
      return null;
    }
    var type = new AppTerm.HoleApp(new LocalVar("_"));
    var abstracted = new LocalVar(var.name() + "'");
    var param = new Term.Param(abstracted, type, arg.explicit());
    subst.add(var, new RefTerm(abstracted));
    return new LamTerm(param, new LamTerm(param, rhs));
  }

  @Override
  public @NotNull Boolean visitHole(AppTerm.@NotNull HoleApp lhs, @NotNull Term rhs, @NotNull Term type) {
    var solved = extract(lhs.args(), rhs);
    if (solved == null) {
      metaContext.report(new HoleBadSpineWarn(lhs, expr));
      return false;
    }
    var solution = metaContext.solutions().getOption(lhs);
    if (solution.isDefined()) return compare(AppTerm.make(solution.get(), lhs.args()), rhs, type);
    metaContext.solutions().put(lhs, solved);
    return true;
  }
}
