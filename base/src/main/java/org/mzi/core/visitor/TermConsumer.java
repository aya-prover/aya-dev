// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.visitor;

import asia.kala.Unit;
import asia.kala.control.Option;
import org.jetbrains.annotations.NotNull;
import org.mzi.core.term.*;
import org.mzi.generic.Arg;
import org.mzi.core.Tele;

public interface TermConsumer<P> extends Term.Visitor<P, Unit>, Tele.Visitor<P, Unit> {
  @Override default Unit visitNamed(Tele.@NotNull NamedTele named, P p) {
    return named.next().accept(this, p);
  }

  @Override
  default Unit visitHole(@NotNull AppTerm.HoleApp term, P p) {
    term.solution().forEach(sol -> sol.accept(this, p));
    term.argsBuf().forEach(arg -> visitArg(arg, p));
    return Unit.unit();
  }

  @Override default Unit visitTyped(Tele.@NotNull TypedTele typed, P p) {
    Option.of(typed.next()).forEach(tele -> tele.accept(this, p) );
    return typed.type().accept(this, p);
  }

  @Override default Unit visitLam(@NotNull LamTerm term, P p) {
    term.tele().accept(this, p);
    return term.body().accept(this, p);
  }

  @Override default Unit visitUniv(@NotNull UnivTerm term, P p) {
    return Unit.unit();
  }

  @Override default Unit visitPi(@NotNull DT.PiTerm term, P p) {
    term.telescope().accept(this, p);
    return term.last().accept(this, p);
  }

  @Override default Unit visitSigma(@NotNull DT.SigmaTerm term, P p) {
    term.telescope().accept(this, p);
    return Unit.unit();
  }

  @Override default Unit visitRef(@NotNull RefTerm term, P p) {
    return Unit.unit();
  }

  default void visitArg(@NotNull Arg<? extends Term> arg, P p) {
    arg.term().accept(this, p);
  }

  @Override default Unit visitApp(@NotNull AppTerm.Apply term, P p) {
    visitArg(term.arg(), p);
    return term.fn().accept(this, p);
  }

  @Override default Unit visitFnCall(@NotNull AppTerm.FnCall fnCall, P p) {
    fnCall.args().forEach(arg -> visitArg(arg, p));
    return fnCall.fn().accept(this, p);
  }

  @Override default Unit visitTup(@NotNull TupTerm term, P p) {
    term.items().forEach(item -> item.accept(this, p));
    return term.accept(this, p);
  }

  @Override default Unit visitProj(@NotNull ProjTerm term, P p) { return term.tup().accept(this, p); }
}
