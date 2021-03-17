// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import org.aya.core.term.*;
import org.aya.generic.Arg;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

public interface TermConsumer<P> extends Term.Visitor<P, Unit> {

  @Override
  default Unit visitHole(@NotNull AppTerm.HoleApp term, P p) {
    term.argsBuf().forEach(arg -> visitArg(arg, p));
    return Unit.unit();
  }

  @Override default Unit visitLam(@NotNull LamTerm term, P p) {
    term.param().type().accept(this, p);
    return term.body().accept(this, p);
  }

  @Override default Unit visitUniv(@NotNull UnivTerm term, P p) {
    return Unit.unit();
  }

  @Override default Unit visitPi(@NotNull PiTerm term, P p) {
    term.param().type().accept(this, p);
    return term.body().accept(this, p);
  }

  @Override default Unit visitSigma(@NotNull SigmaTerm term, P p) {
    term.params().forEach(param -> param.type().accept(this, p));
    return term.body().accept(this, p);
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

  @Override default Unit visitNew(@NotNull NewTerm newTerm, P p) {
    newTerm.params().forEach(t -> t._2.accept(this, p));
    return Unit.unit();
  }

  @Override default Unit visitProj(@NotNull ProjTerm term, P p) {
    return term.tup().accept(this, p);
  }
}
