// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import org.aya.core.pat.Pat;
import org.aya.core.term.*;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TermToPat implements Term.Visitor<Unit, @Nullable Pat> {
  public static final @NotNull TermToPat INSTANCE = new TermToPat();

  private TermToPat() {
  }

  @Override public Pat visitRef(@NotNull RefTerm term, Unit unit) {
    return new Pat.Bind(true, term.var(), term);
  }

  @Override public Pat visitLam(@NotNull IntroTerm.Lambda term, Unit unit) {
    return null;
  }

  @Override public Pat visitPi(@NotNull FormTerm.Pi term, Unit unit) {
    return null;
  }

  @Override public Pat visitSigma(@NotNull FormTerm.Sigma term, Unit unit) {
    return null;
  }

  @Override public Pat visitUniv(@NotNull FormTerm.Univ term, Unit unit) {
    return null;
  }

  @Override public Pat visitApp(@NotNull ElimTerm.App term, Unit unit) {
    return null;
  }

  @Override public Pat visitFnCall(@NotNull CallTerm.Fn fnCall, Unit unit) {
    return null;
  }

  @Override public Pat visitDataCall(@NotNull CallTerm.Data dataCall, Unit unit) {
    return null;
  }

  @Override public Pat visitConCall(@NotNull CallTerm.Con conCall, Unit unit) {
    return new Pat.Ctor(true, conCall.ref(),
      conCall.args().map(at -> at.term().accept(this, unit)), null,
      conCall.head().underlyingDataCall()
    );
  }

  @Override public Pat visitStructCall(@NotNull CallTerm.Struct structCall, Unit unit) {
    return null;
  }

  @Override public Pat visitPrimCall(CallTerm.@NotNull Prim prim, Unit unit) {
    return null;
  }

  @Override public Pat visitTup(@NotNull IntroTerm.Tuple term, Unit unit) {
    return new Pat.Tuple(true,
      term.items().map(t -> t.accept(this, unit)), null, term);
  }

  @Override public Pat visitNew(@NotNull IntroTerm.New newTerm, Unit unit) {
    return null;
  }

  @Override public Pat visitProj(@NotNull ElimTerm.Proj term, Unit unit) {
    return null;
  }

  @Override public Pat visitAccess(@NotNull CallTerm.Access term, Unit unit) {
    return null;
  }

  @Override public Pat visitHole(CallTerm.@NotNull Hole term, Unit unit) {
    return null;
  }
}
