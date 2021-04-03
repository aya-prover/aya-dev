// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.term;

import org.aya.core.pat.Pat;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

final class TermToPat implements Term.Visitor<Unit, Pat> {
  static final @NotNull TermToPat INSTANCE = new TermToPat();

  private TermToPat(){}

  @Override
  public Pat visitRef(@NotNull RefTerm term, Unit unit) {
    return new Pat.Bind(true, term.var(), term);
  }

  @Override
  public Pat visitLam(@NotNull LamTerm term, Unit unit) {
    return new Pat.Absurd(true, term);
  }

  @Override
  public Pat visitPi(@NotNull PiTerm term, Unit unit) {
    return new Pat.Absurd(true, term);
  }

  @Override
  public Pat visitSigma(@NotNull SigmaTerm term, Unit unit) {
    return new Pat.Absurd(true, term);
  }

  @Override
  public Pat visitUniv(@NotNull UnivTerm term, Unit unit) {
    return new Pat.Absurd(true, term);
  }

  @Override
  public Pat visitApp(@NotNull AppTerm term, Unit unit) {
    return new Pat.Absurd(true, term);
  }

  @Override
  public Pat visitFnCall(@NotNull CallTerm.Fn fnCall, Unit unit) {
    return new Pat.Absurd(true, fnCall);
  }

  @Override
  public Pat visitDataCall(@NotNull CallTerm.Data dataCall, Unit unit) {
    return new Pat.Absurd(true, dataCall);
  }

  @Override
  public Pat visitConCall(@NotNull CallTerm.Con conCall, Unit unit) {
    return new Pat.Ctor(true, conCall.ref(),
      conCall.args().map(at -> at.term().accept(this, unit)), null,
      conCall.head().underlyingDataCall()
    );
  }

  @Override
  public Pat visitStructCall(@NotNull CallTerm.Struct structCall, Unit unit) {
    return new Pat.Absurd(true, structCall);
  }

  @Override
  public Pat visitPrimCall(CallTerm.@NotNull Prim prim, Unit unit) {
    return new Pat.Absurd(true, prim);
  }

  @Override
  public Pat visitTup(@NotNull TupTerm term, Unit unit) {
    return new Pat.Tuple(true,
      term.items().map(t -> t.accept(this, unit)), null, term);
  }

  @Override
  public Pat visitNew(@NotNull NewTerm newTerm, Unit unit) {
    return new Pat.Absurd(true, newTerm);
  }

  @Override
  public Pat visitProj(@NotNull ProjTerm term, Unit unit) {
    return new Pat.Absurd(true, term);
  }

  @Override
  public Pat visitHole(CallTerm.@NotNull Hole term, Unit unit) {
    return new Pat.Absurd(true, term);
  }
}
