// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import org.aya.api.util.Arg;
import org.aya.core.term.*;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

public interface TermConsumer<P> extends Term.Visitor<P, Unit> {

  @Override
  default Unit visitHole(@NotNull CallTerm.Hole term, P p) {
    visitArgs(p, term.args());
    return Unit.unit();
  }

  @Override default Unit visitLam(@NotNull IntroTerm.Lambda term, P p) {
    term.param().type().accept(this, p);
    return term.body().accept(this, p);
  }

  @Override default Unit visitUniv(@NotNull FormTerm.Univ term, P p) {
    return Unit.unit();
  }

  @Override default Unit visitPi(@NotNull FormTerm.Pi term, P p) {
    term.param().type().accept(this, p);
    return term.body().accept(this, p);
  }

  @Override default Unit visitSigma(@NotNull FormTerm.Sigma term, P p) {
    term.params().forEach(param -> param.type().accept(this, p));
    return Unit.unit();
  }

  @Override default Unit visitRef(@NotNull RefTerm term, P p) {
    return Unit.unit();
  }

  default void visitArg(@NotNull Arg<? extends Term> arg, P p) {
    arg.term().accept(this, p);
  }

  @Override default Unit visitApp(@NotNull ElimTerm.App term, P p) {
    visitArg(term.arg(), p);
    return term.fn().accept(this, p);
  }

  @Override default Unit visitFnCall(@NotNull CallTerm.Fn fnCall, P p) {
    visitArgs(p, fnCall.args());
    return Unit.unit();
  }

  @Override default Unit visitPrimCall(CallTerm.@NotNull Prim prim, P p) {
    visitArgs(p, prim.args());
    return Unit.unit();
  }


  @Override default Unit visitDataCall(@NotNull CallTerm.Data dataCall, P p) {
    visitArgs(p, dataCall.args());
    return Unit.unit();
  }

  @Override default Unit visitConCall(@NotNull CallTerm.Con conCall, P p) {
    visitArgs(p, conCall.head().dataArgs());
    visitArgs(p, conCall.conArgs());
    return Unit.unit();
  }

  @Override default Unit visitStructCall(@NotNull CallTerm.Struct structCall, P p) {
    visitArgs(p, structCall.args());
    return Unit.unit();
  }

  @Override default Unit visitTup(@NotNull IntroTerm.Tuple term, P p) {
    term.items().forEach(item -> item.accept(this, p));
    return Unit.unit();
  }

  default void visitArgs(P p, SeqLike<Arg<@NotNull Term>> args) {
    args.forEach(arg -> visitArg(arg, p));
  }

  @Override default Unit visitNew(@NotNull IntroTerm.New newTerm, P p) {
    newTerm.params().forEach(t -> t._2.accept(this, p));
    return Unit.unit();
  }

  @Override default Unit visitProj(@NotNull ElimTerm.Proj term, P p) {
    return term.tup().accept(this, p);
  }
}
