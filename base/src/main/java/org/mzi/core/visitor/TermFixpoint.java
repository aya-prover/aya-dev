// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.visitor;

import asia.kala.collection.mutable.Buffer;
import asia.kala.control.Option;
import org.jetbrains.annotations.NotNull;
import org.mzi.core.Tele;
import org.mzi.core.term.*;
import org.mzi.generic.Arg;
import org.mzi.tyck.sort.Sort;

/**
 * @author ice1000
 */
public interface TermFixpoint<P> extends
  Term.Visitor<P, @NotNull Term>,
  Tele.Visitor<P, @NotNull Tele> {
  @Override default @NotNull Tele visitNamed(Tele.@NotNull NamedTele named, P p) {
    var next = named.next().accept(this, p);
    if (next == named.next()) return named;
    return new Tele.NamedTele(named.ref(), next);
  }

  @Override default @NotNull Tele visitTyped(Tele.@NotNull TypedTele typed, P p) {
    var next = Option.of(typed.next()).map(tele -> tele.accept(this, p)).getOrNull();
    var type = typed.type().accept(this, p);
    if (next == typed.next() && type == typed.type()) return typed;
    return new Tele.TypedTele(typed.ref(), type, typed.explicit(), next);
  }

  @Override default @NotNull Term visitHole(@NotNull AppTerm.HoleApp term, P p) {
    var sol = term.solution().getOrNull();
    var args = term.argsBuf().view().map(arg -> visitArgUncapture(arg, p));
    if (sol != null && !args.sameElements(term.argsBuf())) {
      var newSol = sol.accept(this, p);
      if (newSol != sol) return new AppTerm.HoleApp(newSol, term.var(), args.collect(Buffer.factory()));
    }
    return term;
  }

  @Override default @NotNull Term visitLam(@NotNull LamTerm term, P p) {
    var telescope = term.tele().accept(this, p);
    var body = term.body().accept(this, p);
    if (telescope == term.tele() && body == term.body()) return term;
    return new LamTerm(telescope, body);
  }

  @Override default @NotNull Term visitUniv(@NotNull UnivTerm term, P p) {
    var sort = visitSort(term.sort(), p);
    if (sort == term.sort()) return term;
    return new UnivTerm(sort);
  }

  @Override default @NotNull Term visitPi(@NotNull DT.PiTerm term, P p) {
    var telescope = term.telescope().accept(this, p);
    var last = term.last().accept(this, p);
    if (telescope == term.telescope() && last == term.last()) return term;
    return new DT.PiTerm(telescope, last, term.co());
  }

  @Override default @NotNull Term visitSigma(@NotNull DT.SigmaTerm term, P p) {
    var telescope = term.telescope().accept(this, p);
    if (telescope == term.telescope()) return term;
    return new DT.SigmaTerm(telescope, term.co());
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
    return new AppTerm.Apply(function, arg);
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
