// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.unify;

import org.aya.api.ref.Var;
import org.aya.core.def.DataDef;
import org.aya.core.def.Def;
import org.aya.core.sort.Sort;
import org.aya.core.term.*;
import org.aya.core.visitor.Substituter;
import org.aya.core.visitor.Unfolder;
import org.aya.generic.Level;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.error.HoleBadSpineWarn;
import org.aya.tyck.error.RecursiveSolutionError;
import org.aya.util.Decision;
import org.aya.util.Ordering;
import org.glavo.kala.collection.immutable.ImmutableSeq;
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
  private final @NotNull Ordering cmp;

  public boolean compare(@NotNull Term lhs, @NotNull Term rhs, @NotNull Term type) {
    return lhs.accept(this, rhs, type);
  }

  @Override public @NotNull Boolean visitRef(@NotNull RefTerm lhs, @NotNull Term preRhs, @NotNull Term type) {
    return passDown(lhs, preRhs, type);
  }

  @Override public @NotNull Boolean visitLam(@NotNull IntroTerm.Lambda lhs, @NotNull Term preRhs, @NotNull Term type) {
    throw new IllegalStateException("No visitLam in PatDefEq");
  }

  @Override public @NotNull Boolean visitPi(@NotNull FormTerm.Pi lhs, @NotNull Term preRhs, @NotNull Term type) {
    if (!(preRhs instanceof FormTerm.Pi rhs)) return false;
    return defeq.checkParam(lhs.param(), rhs.param(), FormTerm.Univ.OMEGA, () -> false, () ->
      defeq.compare(lhs.body(), rhs.body(), FormTerm.Univ.OMEGA));
  }

  @Override public @NotNull Boolean visitSigma(@NotNull FormTerm.Sigma lhs, @NotNull Term preRhs, @NotNull Term type) {
    if (!(preRhs instanceof FormTerm.Sigma rhs)) return false;
    return defeq.checkParams(lhs.params(), rhs.params(), () -> false, () -> true);
  }

  @Override public @NotNull Boolean visitUniv(@NotNull FormTerm.Univ lhs, @NotNull Term preRhs, @NotNull Term type) {
    if (!(preRhs instanceof FormTerm.Univ rhs)) return false;
    defeq.tycker.equations.add(lhs.sort(), rhs.sort(), cmp, defeq.pos);
    return true;
  }

  @Override public @NotNull Boolean visitApp(@NotNull ElimTerm.App lhs, @NotNull Term preRhs, @NotNull Term type) {
    return passDown(lhs, preRhs, type);
  }

  @Override public @NotNull Boolean visitFnCall(@NotNull CallTerm.Fn lhs, @NotNull Term preRhs, @NotNull Term type) {
    if (!(preRhs instanceof CallTerm.Fn rhs) || lhs.ref() != rhs.ref())
      return (lhs.whnf() != Decision.YES || preRhs.whnf() != Decision.YES)
        && defeq.compareWHNF(lhs, preRhs, type);
    // Lossy comparison
    levels(lhs.sortArgs(), rhs.sortArgs());
    if (defeq.visitArgs(lhs.args(), rhs.args(), Def.defTele(lhs.ref()))) return true;
    return defeq.compareWHNF(lhs, rhs, type);
  }

  @Override
  public @NotNull Boolean visitDataCall(@NotNull CallTerm.Data lhs, @NotNull Term preRhs, @NotNull Term type) {
    if (!(preRhs instanceof CallTerm.Data rhs) || lhs.ref() != rhs.ref()) return false;
    levels(lhs.sortArgs(), rhs.sortArgs());
    return defeq.visitArgs(lhs.args(), rhs.args(), Def.defTele(lhs.ref()));
  }

  @Override
  public @NotNull Boolean visitStructCall(@NotNull CallTerm.Struct lhs, @NotNull Term preRhs, @NotNull Term type) {
    if (!(preRhs instanceof CallTerm.Struct rhs) || lhs.ref() != rhs.ref()) return false;
    levels(lhs.sortArgs(), rhs.sortArgs());
    return defeq.visitArgs(lhs.args(), rhs.args(), Def.defTele(lhs.ref()));
  }

  private void levels(ImmutableSeq<@NotNull Level<Sort.LvlVar>> l, ImmutableSeq<@NotNull Level<Sort.LvlVar>> r) {
    for (var levels : l.zip(r)) defeq.tycker.equations.add(levels._1, levels._2, cmp, defeq.pos);
  }

  @Override
  public @NotNull Boolean visitPrimCall(CallTerm.@NotNull Prim lhs, @NotNull Term preRhs, @NotNull Term type) {
    if (!(preRhs instanceof CallTerm.Prim rhs) || lhs.ref() != rhs.ref())
      return (lhs.whnf() != Decision.YES || preRhs.whnf() != Decision.YES)
        && defeq.compareWHNF(lhs, preRhs, type);
    // Lossy comparison
    if (defeq.visitArgs(lhs.args(), rhs.args(), Def.defTele(lhs.ref()))) return true;
    return defeq.compareWHNF(lhs, rhs, type);
  }

  public @NotNull Boolean visitConCall(@NotNull CallTerm.Con lhs, @NotNull Term preRhs, @NotNull Term type) {
    if (!(preRhs instanceof CallTerm.Con rhs) || lhs.ref() != rhs.ref())
      return (lhs.whnf() != Decision.YES || preRhs.whnf() != Decision.YES)
        && defeq.compareWHNF(lhs, preRhs, type);
    levels(lhs.sortArgs(), rhs.sortArgs());
    return defeq.visitArgs(lhs.conArgs(), rhs.conArgs(), DataDef.Ctor.conTele(lhs.ref()));
  }

  @Override public @NotNull Boolean visitTup(@NotNull IntroTerm.Tuple lhs, @NotNull Term preRhs, @NotNull Term type) {
    throw new IllegalStateException("No visitTup in TermDirectedDefEq");
  }

  @Override public @NotNull Boolean visitNew(@NotNull IntroTerm.New newTerm, @NotNull Term term, @NotNull Term term2) {
    throw new IllegalStateException("No visitStruct in TermDirectedDefEq");
  }

  @Override public @NotNull Boolean visitProj(@NotNull ElimTerm.Proj lhs, @NotNull Term preRhs, @NotNull Term type) {
    return passDown(lhs, preRhs, type);
  }

  @Override
  public @NotNull Boolean visitAccess(@NotNull CallTerm.Access lhs, @NotNull Term preRhs, @NotNull Term type) {
    return passDown(lhs, preRhs, type);
  }

  private @NotNull Boolean passDown(@NotNull Term lhs, @NotNull Term preRhs, @NotNull Term type) {
    var inferred = untypedDefeq.compare(lhs, preRhs);
    if (inferred == null) return false;
    return defeq.compare(inferred, type, FormTerm.Univ.OMEGA); // TODO[xyr]: proper subtyping?
  }

  public PatDefEq(@NotNull TypedDefEq defeq, @NotNull Ordering cmp) {
    this.defeq = defeq;
    this.untypedDefeq = new UntypedDefEq(defeq, cmp);
    this.cmp = cmp;
  }

  private @Nullable Term extract(CallTerm.@NotNull Hole lhs, Term rhs) {
    var subst = new Substituter.TermSubst(new MutableHashMap<>(/*spine.size() * 2*/));
    var meta = lhs.ref().core();
    for (var arg : lhs.args().view().zip(meta.telescope)) {
      if (arg._1.term() instanceof RefTerm ref) {
        // TODO[xyr]: do scope checking here
        subst.add(ref.var(), new RefTerm(arg._2.ref()));
        if (rhs == null) return null;
      } else return null;
      // TODO[ice]: ^ eta var
    }
    var correspondence = MutableHashMap.<Var, Term>of();
    defeq.varSubst.forEach((k, v) -> correspondence.set(k, new RefTerm(v)));
    return rhs.subst(subst.add(Unfolder.buildSubst(meta.contextTele, lhs.contextArgs())).add(new Substituter.TermSubst(correspondence)));
  }

  @Override public @NotNull Boolean visitHole(CallTerm.@NotNull Hole lhs, @NotNull Term rhs, @NotNull Term type) {
    var meta = lhs.ref().core();
    if (rhs instanceof CallTerm.Hole rcall && lhs.ref() == rcall.ref()) {
      var holeTy = FormTerm.Pi.make(false, meta.telescope, meta.result);
      for (var arg : lhs.args().view().zip(rcall.args())) {
        if (!(holeTy instanceof FormTerm.Pi holePi))
          throw new IllegalStateException("meta arg size larger than param size. this should not happen");
        if (!defeq.compare(arg._1.term(), arg._2.term(), holePi.param().type())) return false;
        holeTy = holePi.substBody(arg._1.term());
      }
      return true;
    }
    var solved = extract(lhs, rhs);
    if (solved == null) {
      defeq.tycker.reporter.report(new HoleBadSpineWarn(lhs, defeq.pos));
      return false;
    }
    assert meta.body == null;
    untypedDefeq.compare(solved.synth(meta.contextTele), meta.result);
    var success = meta.solve(lhs.ref(), solved);
    if (!success) {
      defeq.tycker.reporter.report(new RecursiveSolutionError(lhs.ref(), solved, untypedDefeq.defeq().pos));
      throw new ExprTycker.TyckInterruptedException();
    }
    return true;
  }
}
