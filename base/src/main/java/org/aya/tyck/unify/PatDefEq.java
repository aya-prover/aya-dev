// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.unify;

import org.aya.core.def.Def;
import org.aya.core.term.*;
import org.aya.core.visitor.Substituter;
import org.aya.generic.Arg;
import org.aya.ref.LocalVar;
import org.aya.tyck.MetaContext;
import org.aya.tyck.error.HoleBadSpineWarn;
import org.aya.util.Constants;
import org.aya.util.Decision;
import org.aya.util.Ordering;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The implementation of untyped pattern unification for holes.
 * András Kovács' elaboration-zoo is taken as reference.
 *
 * @author re-xyr, ice1000
 */
public final class PatDefEq implements Term.BiVisitor<@NotNull Term, @NotNull Term, @NotNull Boolean> {
  private final @NotNull TypedDefEq defeq;
  private final @NotNull UntypedDefEq untypedDefeq;

  private final @NotNull Ordering ord;
  private final @NotNull MetaContext metaContext;

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
  public @NotNull Boolean visitApp(@NotNull AppTerm lhs, @NotNull Term preRhs, @NotNull Term type) {
    return passDown(lhs, preRhs, type);
  }

  @Override
  public @NotNull Boolean visitFnCall(@NotNull CallTerm.FnCall lhs, @NotNull Term preRhs, @NotNull Term type) {
    if (!(preRhs instanceof CallTerm.FnCall rhs) || lhs.fnRef() != rhs.fnRef()) {
      if (lhs.whnf() != Decision.NO) return false;
      return defeq.compareWHNF(lhs, preRhs, type);
    }
    return defeq.visitArgs(lhs.args(), rhs.args(), Def.defTele(lhs.fnRef()));
  }

  @Override
  public @NotNull Boolean visitDataCall(@NotNull CallTerm.DataCall lhs, @NotNull Term preRhs, @NotNull Term type) {
    if (!(preRhs instanceof CallTerm.DataCall rhs) || lhs.dataRef() != rhs.dataRef()) return false;
    return defeq.visitArgs(lhs.args(), rhs.args(), Def.defTele(lhs.dataRef()));
  }

  @Override
  public @NotNull Boolean visitConCall(@NotNull CallTerm.ConCall lhs, @NotNull Term preRhs, @NotNull Term type) {
    if (!(preRhs instanceof CallTerm.ConCall rhs) || lhs.conHead() != rhs.conHead()) return false;
    return defeq.visitArgs(lhs.dataArgs(), rhs.dataArgs(), Def.defTele(lhs.dataRef()))
      && defeq.visitArgs(lhs.conArgs(), rhs.conArgs(), Def.defTele(lhs.conHead()));
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
    var type = new CallTerm.HoleApp(new LocalVar(Constants.ANONYMOUS_PREFIX));
    var abstracted = new LocalVar(var.name() + "'");
    var param = new Term.Param(abstracted, type, arg.explicit());
    subst.add(var, new RefTerm(abstracted));
    return new LamTerm(param, new LamTerm(param, rhs));
  }

  @Override
  public @NotNull Boolean visitHole(CallTerm.@NotNull HoleApp lhs, @NotNull Term rhs, @NotNull Term type) {
    var solved = extract(lhs.args(), rhs);
    if (solved == null) {
      metaContext.report(new HoleBadSpineWarn(lhs, defeq.pos));
      return false;
    }
    var solution = metaContext.solutions().getOption(lhs);
    if (solution.isDefined()) return compare(CallTerm.make(solution.get(), lhs.args()), rhs, type);
    metaContext.solutions().put(lhs, solved);
    return true;
  }
}
