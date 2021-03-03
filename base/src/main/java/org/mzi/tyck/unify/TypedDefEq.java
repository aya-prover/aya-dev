// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.tyck.unify;

import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.Expr;
import org.mzi.core.term.*;
import org.mzi.tyck.MetaContext;
import org.mzi.util.Decision;
import org.mzi.util.Ordering;

/**
 * @author re-xyr
 */
public abstract class TypedDefEq implements Term.BiVisitor<@NotNull Term, @NotNull Term, @NotNull Boolean> {
  private final @NotNull TypeDirectedDefEq defeq;
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
    throw new IllegalStateException("No visitLam in TermDirectedDefEq");
  }

  @Override
  public @NotNull Boolean visitPi(@NotNull PiTerm lhs, @NotNull Term preRhs, @NotNull Term type) {
    /*
    if (!(preRhs instanceof PiTerm rhs)) return false;
    if (!defeq.checkParam(lhs.param(), rhs.param())) return false;
    var bodyIsOk = defeq.compare(lhs.body(), rhs.body(), UnivTerm.OMEGA);
    defeq.localCtx.remove(lhs.param().ref());
    return bodyIsOk;
     */
    return passDown(lhs, preRhs, type);
  }

  @Override
  public @NotNull Boolean visitSigma(@NotNull SigmaTerm lhs, @NotNull Term preRhs, @NotNull Term type) {
    /*if (!(preRhs instanceof SigmaTerm rhs)) return false;
    if (!defeq.checkParams(lhs.params(), rhs.params())) return false;
    var bodyIsOk = defeq.compare(lhs.body(), rhs.body(), UnivTerm.OMEGA);
    lhs.params().forEach(param -> defeq.localCtx.remove(param.ref()));
    return bodyIsOk;

     */
    return passDown(lhs, preRhs, type);
  }

  @Override
  public @NotNull Boolean visitUniv(@NotNull UnivTerm lhs, @NotNull Term preRhs, @NotNull Term type) {
    /*
    if (!(preRhs instanceof UnivTerm rhs)) return false;
    return true;

     */
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

  @Override
  public @NotNull Boolean visitHole(AppTerm.@NotNull HoleApp lhs, @NotNull Term preRhs, @NotNull Term type) {
    return passDown(lhs, preRhs, type);
  }

  private @NotNull Boolean passDown(@NotNull Term lhs, @NotNull Term preRhs, @NotNull Term type) {
    var inferred = untypedDefeq.compare(lhs, preRhs);
    if (inferred == null) return false;
    return defeq.compare(inferred, type, UnivTerm.OMEGA); // TODO[xyr]: proper subtyping?
  }

  public TypedDefEq(@NotNull TypeDirectedDefEq defeq, @NotNull Ordering ord, @NotNull MetaContext metaContext) {
    this.defeq = defeq;
    this.untypedDefeq = new UntypedDefEq(defeq);
    this.ord = ord;
    this.metaContext = metaContext;
  }
}
