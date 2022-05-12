// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.SeqLike;
import kala.tuple.Unit;
import org.aya.core.term.*;
import org.aya.generic.Arg;
import org.jetbrains.annotations.NotNull;

public interface TermConsumer<P> extends Term.Visitor<P, Unit> {
  default void visitCall(@NotNull CallTerm call, P p) {
  }

  @Override default Unit visitHole(@NotNull CallTerm.Hole term, P p) {
    visitArgs(p, term.args());
    visitArgs(p, term.contextArgs());
    visitCall(term, p);
    // TODO[ice]: is it fine? Maybe we want to visit the solutions as well?
    // var body = term.ref().body;
    // if (body != null) body.accept(this, p);
    return Unit.unit();
  }

  @Override default Unit visitFieldRef(@NotNull RefTerm.Field term, P p) {
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
    return term.of().accept(this, p);
  }

  @Override default Unit visitFnCall(@NotNull CallTerm.Fn fnCall, P p) {
    visitArgs(p, fnCall.args());
    visitCall(fnCall, p);
    return Unit.unit();
  }

  @Override default Unit visitPrimCall(CallTerm.@NotNull Prim prim, P p) {
    visitArgs(p, prim.args());
    visitCall(prim, p);
    return Unit.unit();
  }


  @Override default Unit visitDataCall(@NotNull CallTerm.Data dataCall, P p) {
    visitArgs(p, dataCall.args());
    visitCall(dataCall, p);
    return Unit.unit();
  }

  @Override default Unit visitConCall(@NotNull CallTerm.Con conCall, P p) {
    visitArgs(p, conCall.head().dataArgs());
    visitArgs(p, conCall.conArgs());
    visitCall(conCall, p);
    return Unit.unit();
  }

  @Override default Unit visitStructCall(@NotNull CallTerm.Struct structCall, P p) {
    visitArgs(p, structCall.args());
    visitCall(structCall, p);
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
    newTerm.struct().accept(this, p);
    newTerm.params().forEach((k, v) -> v.accept(this, p));
    return Unit.unit();
  }

  @Override default Unit visitError(@NotNull ErrorTerm term, P p) {
    return Unit.unit();
  }

  @Override default Unit visitMetaPat(RefTerm.@NotNull MetaPat metaPat, P p) {
    return Unit.unit();
  }

  @Override
  default Unit visitInterval(FormTerm.@NotNull Interval interval, P p) {
    return Unit.unit();
  }

  @Override default Unit visitEnd(PrimTerm.@NotNull End end, P p) {
    return Unit.unit();
  }

  @Override default Unit visitProj(@NotNull ElimTerm.Proj term, P p) {
    return term.of().accept(this, p);
  }

  @Override default Unit visitAccess(@NotNull CallTerm.Access term, P p) {
    visitArgs(p, term.fieldArgs());
    visitCall(term, p);
    return term.of().accept(this, p);
  }

  @Override default Unit visitShapedLit(LitTerm.@NotNull ShapedInt shaped, P p) {
    shaped.type().accept(this, p);
    return Unit.unit();
  }
}
