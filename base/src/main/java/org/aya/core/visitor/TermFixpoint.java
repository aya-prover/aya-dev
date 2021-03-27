// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import org.aya.api.util.Arg;
import org.aya.core.term.*;
import org.aya.tyck.sort.Sort;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.tuple.Tuple;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;

/**
 * @author ice1000
 */
public interface TermFixpoint<P> extends Term.Visitor<P, @NotNull Term> {
  @Override default @NotNull Term visitHole(@NotNull CallTerm.Hole term, P p) {
    var args = term.argsBuf().view().map(arg -> visitArg(arg, p));
    if (!args.sameElements(term.argsBuf())) {
      return new CallTerm.Hole(term.ref(), args.collect(Buffer.factory()));
    }
    return term;
  }

  @Override default @NotNull Term visitDataCall(@NotNull CallTerm.Data dataCall, P p) {
    var contextArgs = dataCall.contextArgs().map(arg -> visitArg(arg, p));
    var args = dataCall.args().map(arg -> visitArg(arg, p));
    if (dataCall.contextArgs().sameElements(contextArgs, true)
      && dataCall.args().sameElements(args, true)) return dataCall;
    return new CallTerm.Data(dataCall.ref(), contextArgs, args);
  }

  @Override default @NotNull Term visitConCall(@NotNull CallTerm.Con conCall, P p) {
    var contextArgs = conCall.head().contextArgs().map(arg -> visitArg(arg, p));
    var dataArgs = conCall.head().dataArgs().map(arg -> visitArg(arg, p));
    var conArgs = conCall.conArgs().map(arg -> visitArg(arg, p));
    var head = new CallTerm.ConHead(conCall.head().dataRef(), conCall.head().ref(), contextArgs, dataArgs);
    return new CallTerm.Con(head, conArgs);
  }

  @Override default @NotNull Term visitStructCall(@NotNull CallTerm.Struct structCall, P p) {
    var contextArgs = structCall.contextArgs().view().map(arg -> visitArg(arg, p));
    var args = structCall.args().view().map(arg -> visitArg(arg, p));
    if (structCall.contextArgs().sameElements(contextArgs, true)
      && structCall.args().sameElements(args, true)) return structCall;
    return new CallTerm.Struct(structCall.ref(), contextArgs, args);
  }

  private <T> T visitParameterized(
    @NotNull Term.Param theParam, @NotNull Term theBody, @NotNull P p, @NotNull T original,
    @NotNull BiFunction<Term.@NotNull Param, @NotNull Term, T> callback
  ) {
    var param = new Term.Param(theParam.ref(), theParam.type().accept(this, p), theParam.explicit());
    var body = theBody.accept(this, p);
    if (param.type() == theParam.type() && body == theBody) return original;
    return callback.apply(param, body);
  }

  @Override default @NotNull Term visitLam(@NotNull LamTerm term, P p) {
    return visitParameterized(term.param(), term.body(), p, term, LamTerm::new);
  }

  @Override default @NotNull Term visitUniv(@NotNull UnivTerm term, P p) {
    var sort = visitSort(term.sort(), p);
    if (sort == term.sort()) return term;
    return new UnivTerm(sort);
  }

  @Override default @NotNull Term visitPi(@NotNull PiTerm term, P p) {
    return visitParameterized(term.param(), term.body(), p, term, (a, t) -> new PiTerm(term.co(), a, t));
  }

  @Override default @NotNull Term visitSigma(@NotNull SigmaTerm term, P p) {
    var params = term.params().map(param ->
      new Term.Param(param.ref(), param.type().accept(this, p), param.explicit()));
    var body = term.body().accept(this, p);
    if (params.sameElements(term.params(), true) && body == term.body()) return term;
    return new SigmaTerm(term.co(), params, body);
  }

  @Override default @NotNull Term visitRef(@NotNull RefTerm term, P p) {
    return term;
  }

  default @NotNull Arg<Term> visitArg(@NotNull Arg<Term> arg, P p) {
    var term = arg.term().accept(this, p);
    if (term == arg.term()) return arg;
    return new Arg<>(term, arg.explicit());
  }

  default @NotNull Sort visitSort(@NotNull Sort sort, P p) {
    return sort;
  }

  @Override default @NotNull Term visitApp(@NotNull AppTerm term, P p) {
    var function = term.fn().accept(this, p);
    var arg = visitArg(term.arg(), p);
    if (function == term.fn() && arg == term.arg()) return term;
    return CallTerm.make(function, arg);
  }

  @Override default @NotNull Term visitFnCall(CallTerm.@NotNull Fn fnCall, P p) {
    var contextArgs = fnCall.contextArgs().view().map(arg -> visitArg(arg, p));
    var args = fnCall.args().view().map(arg -> visitArg(arg, p));
    if (fnCall.args().sameElements(args, true)
      && fnCall.args().sameElements(args, true)) return fnCall;
    return new CallTerm.Fn(fnCall.ref(), contextArgs, args);
  }

  @Override default @NotNull Term visitPrimCall(CallTerm.@NotNull Prim prim, P p) {
    var args = prim.args().view().map(arg -> visitArg(arg, p));
    if (prim.args().sameElements(args, true) && prim.args().sameElements(args, true)) return prim;
    return new CallTerm.Prim(prim.ref(), args);
  }

  @Override default @NotNull Term visitTup(@NotNull TupTerm term, P p) {
    var items = term.items().map(x -> x.accept(this, p));
    if (term.items().sameElements(items, true)) return term;
    return new TupTerm(items);
  }

  @Override default @NotNull Term visitNew(@NotNull NewTerm struct, P p) {
    var items = struct.params()
      .map(t -> Tuple.of(t._1, t._2.accept(this, p)));
    if (struct.params().sameElements(items, true)) return struct;
    return new NewTerm(items);
  }

  @Override default @NotNull Term visitProj(@NotNull ProjTerm term, P p) {
    var tuple = term.tup().accept(this, p);
    if (tuple == term.tup()) return term;
    return new ProjTerm(tuple, term.ix());
  }
}
