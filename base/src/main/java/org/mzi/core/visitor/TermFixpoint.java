// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.visitor;

import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;
import org.mzi.core.Param;
import org.mzi.core.term.*;
import org.mzi.generic.Arg;
import org.mzi.tyck.sort.Sort;

import java.util.function.BiFunction;

/**
 * @author ice1000
 */
public interface TermFixpoint<P> extends
  Term.Visitor<P, @NotNull Term> {
  @Override default @NotNull Term visitHole(@NotNull AppTerm.HoleApp term, P p) {
    var args = term.argsBuf().view().map(arg -> visitArgUncapture(arg, p));
    if (!args.sameElements(term.argsBuf())) {
      return new AppTerm.HoleApp(term.var(), args.collect(Buffer.factory()));
    }
    return term;
  }

  @Override default @NotNull Term visitDataCall(@NotNull AppTerm.DataCall dataCall, P p) {
    var args = dataCall.args().view().map(arg -> visitArg(arg, p));
    if (dataCall.args().sameElements(args, true)) return dataCall;
    return new AppTerm.DataCall(dataCall.dataRef(), args);
  }

  private <T> T visitParameterized(
    @NotNull Param theParam, @NotNull Term theBody, @NotNull P p, @NotNull T original,
    @NotNull BiFunction<@NotNull Param, @NotNull Term, T> callback
  ) {
    var param = new Param(theParam.ref(), theParam.type().accept(this, p), theParam.explicit());
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
      new Param(param.ref(), param.type().accept(this, p), param.explicit()));
    var body = term.body().accept(this, p);
    if (params.sameElements(term.params(), true) || body == term.body()) return term;
    return new SigmaTerm(term.co(), params, body);
  }

  @Override default @NotNull Term visitRef(@NotNull RefTerm term, P p) {
    return term;
  }

  default @NotNull Arg<Term> visitArgUncapture(@NotNull Arg<Term> arg, P p) {
    var term = arg.term().accept(this, p);
    if (term == arg.term()) return arg;
    return new Arg<>(term, arg.explicit());
  }

  default @NotNull Arg<? extends Term> visitArg(@NotNull Arg<? extends Term> arg, P p) {
    var term = arg.term().accept(this, p);
    if (term == arg.term()) return arg;
    return new Arg<>(term, arg.explicit());
  }

  default @NotNull Sort visitSort(@NotNull Sort sort, P p) {
    return sort;
  }

  @Override default @NotNull Term visitApp(AppTerm.@NotNull Apply term, P p) {
    var function = term.fn().accept(this, p);
    var arg = visitArg(term.arg(), p);
    if (function == term.fn() && arg == term.arg()) return term;
    return AppTerm.make(function, arg);
  }

  @Override default @NotNull Term visitFnCall(AppTerm.@NotNull FnCall fnCall, P p) {
    var args = fnCall.args().view().map(arg -> visitArg(arg, p));
    if (fnCall.args().sameElements(args, true)) return fnCall;
    return new AppTerm.FnCall(fnCall.fnRef(), args);
  }

  @Override default @NotNull Term visitTup(@NotNull TupTerm term, P p) {
    var items = term.items().map(x -> x.accept(this, p));
    if (term.items().sameElements(items, true)) return term;
    return new TupTerm(items);
  }

  @Override default @NotNull Term visitProj(@NotNull ProjTerm term, P p) {
    var tuple = term.tup().accept(this, p);
    if (tuple == term.tup()) return term;
    return new ProjTerm(tuple, term.ix());
  }
}
