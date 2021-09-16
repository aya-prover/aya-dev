// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import kala.collection.immutable.ImmutableMap;
import kala.tuple.Tuple;
import org.aya.api.util.Arg;
import org.aya.core.sort.Sort;
import org.aya.core.term.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

/**
 * @author ice1000
 */
public interface TermFixpoint<P> extends Term.Visitor<P, @NotNull Term> {
  @Override default @NotNull Term visitHole(@NotNull CallTerm.Hole term, P p) {
    var contextArgs = term.contextArgs().map(arg -> visitArg(arg, p));
    var args = term.args().map(arg -> visitArg(arg, p));
    if (term.contextArgs().sameElements(contextArgs, true)
      && term.args().sameElements(args, true)) return term;
    return new CallTerm.Hole(term.ref(), contextArgs, args);
  }
  @Override
  @NotNull
  default Term visitFieldRef(Term.@NotNull FieldRefTerm term, P p) {
    var ty = term.type().accept(this, p);
    if (ty == term.type()) return term;
    return new Term.FieldRefTerm(term.ref(), ty);
  }

  @Override default @NotNull Term visitDataCall(@NotNull CallTerm.Data dataCall, P p) {
    var args = dataCall.args().map(arg -> visitArg(arg, p));
    var sortArgs = dataCall.sortArgs().mapNotNull(sort -> visitSort(sort, p));
    if (!sortArgs.sizeEquals(dataCall.sortArgs().size())) return new ErrorTerm(dataCall);
    if (dataCall.sortArgs().sameElements(sortArgs, true)
      && dataCall.args().sameElements(args, true)) return dataCall;
    return new CallTerm.Data(dataCall.ref(), sortArgs, args);
  }

  @Override default @NotNull ErrorTerm visitError(@NotNull ErrorTerm term, P p) {
    return term;
  }

  @Override default @NotNull Term visitConCall(@NotNull CallTerm.Con conCall, P p) {
    var dataArgs = conCall.head().dataArgs().map(arg -> visitArg(arg, p));
    var conArgs = conCall.conArgs().map(arg -> visitArg(arg, p));
    var sortArgs = conCall.sortArgs().mapNotNull(sort -> visitSort(sort, p));
    if (!sortArgs.sizeEquals(conCall.sortArgs().size())) return new ErrorTerm(conCall);
    if (conCall.head().dataArgs().sameElements(dataArgs, true)
      && conCall.sortArgs().sameElements(sortArgs, true)
      && conCall.conArgs().sameElements(conArgs, true)) return conCall;
    var head = new CallTerm.ConHead(conCall.head().dataRef(), conCall.head().ref(),
      sortArgs, dataArgs);
    return new CallTerm.Con(head, conArgs);
  }

  @Override default @NotNull Term visitStructCall(@NotNull CallTerm.Struct structCall, P p) {
    var args = structCall.args().map(arg -> visitArg(arg, p));
    var sortArgs = structCall.sortArgs().mapNotNull(sort -> visitSort(sort, p));
    if (!sortArgs.sizeEquals(structCall.sortArgs().size())) return new ErrorTerm(structCall);
    if (structCall.sortArgs().sameElements(sortArgs, true)
      && structCall.args().sameElements(args, true)) return structCall;
    return new CallTerm.Struct(structCall.ref(), sortArgs, args);
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

  @Override default @NotNull Term visitLam(@NotNull IntroTerm.Lambda term, P p) {
    return visitParameterized(term.param(), term.body(), p, term, IntroTerm.Lambda::new);
  }

  @Override default @NotNull Term visitUniv(@NotNull FormTerm.Univ term, P p) {
    var sort = visitSort(term.sort(), p);
    if (sort == null) return new ErrorTerm(term);
    if (sort == term.sort()) return term;
    return new FormTerm.Univ(sort);
  }

  @Override default @NotNull Term visitPi(@NotNull FormTerm.Pi term, P p) {
    return visitParameterized(term.param(), term.body(), p, term, FormTerm.Pi::new);
  }

  @Override default @NotNull Term visitSigma(@NotNull FormTerm.Sigma term, P p) {
    var params = term.params().map(param ->
      new Term.Param(param.ref(), param.type().accept(this, p), param.explicit()));
    if (params.sameElements(term.params(), true)) return term;
    return new FormTerm.Sigma(params);
  }

  @Override default @NotNull Term visitRef(@NotNull RefTerm term, P p) {
    var ty = term.type().accept(this, p);
    if (ty == term.type()) return term;
    return new RefTerm(term.var(), ty);
  }

  default @NotNull Arg<Term> visitArg(@NotNull Arg<Term> arg, P p) {
    var term = arg.term().accept(this, p);
    if (term == arg.term()) return arg;
    return new Arg<>(term, arg.explicit());
  }

  default @Nullable Sort visitSort(@NotNull Sort sort, P p) {
    return sort;
  }

  @Override default @NotNull Term visitApp(@NotNull ElimTerm.App term, P p) {
    var function = term.of().accept(this, p);
    var arg = visitArg(term.arg(), p);
    if (function == term.of() && arg == term.arg()) return term;
    return CallTerm.make(function, arg);
  }

  @Override default @NotNull Term visitFnCall(CallTerm.@NotNull Fn fnCall, P p) {
    var args = fnCall.args().map(arg -> visitArg(arg, p));
    var sortArgs = fnCall.sortArgs().mapNotNull(sort -> visitSort(sort, p));
    if (!sortArgs.sizeEquals(fnCall.sortArgs().size())) return new ErrorTerm(fnCall);
    if (fnCall.sortArgs().sameElements(sortArgs, true)
      && fnCall.args().sameElements(args, true)) return fnCall;
    return new CallTerm.Fn(fnCall.ref(), sortArgs, args);
  }

  @Override default @NotNull Term visitPrimCall(CallTerm.@NotNull Prim prim, P p) {
    var args = prim.args().map(arg -> visitArg(arg, p));
    var sortArgs = prim.sortArgs().mapNotNull(sort -> visitSort(sort, p));
    if (!sortArgs.sizeEquals(prim.sortArgs().size())) return new ErrorTerm(prim);
    if (prim.args().sameElements(args, true)
      && prim.sortArgs().sameElements(sortArgs, true)) return prim;
    return new CallTerm.Prim(prim.ref(), sortArgs, args);
  }

  @Override default @NotNull Term visitTup(@NotNull IntroTerm.Tuple term, P p) {
    var items = term.items().map(x -> x.accept(this, p));
    if (term.items().sameElements(items, true)) return term;
    return new IntroTerm.Tuple(items);
  }

  @Override default @NotNull Term visitNew(@NotNull IntroTerm.New struct, P p) {
    var itemsView = struct.params().view()
      .map((k, v) -> Tuple.of(k, v.accept(this, p)));
    var items = ImmutableMap.from(itemsView);
    // if (struct.params().view().sameElements(items, true)) return struct;
    return new IntroTerm.New(struct.struct(), items);
  }

  @Override default @NotNull Term visitProj(@NotNull ElimTerm.Proj term, P p) {
    var tuple = term.of().accept(this, p);
    if (tuple == term.of()) return term;
    return new ElimTerm.Proj(tuple, term.ix());
  }

  @Override default @NotNull Term visitAccess(@NotNull CallTerm.Access term, P p) {
    var tuple = term.of().accept(this, p);
    var args = term.fieldArgs().map(arg -> visitArg(arg, p));
    var structArgs = term.structArgs().map(arg -> visitArg(arg, p));
    if (term.fieldArgs().sameElements(args, true)
      && term.structArgs().sameElements(structArgs, true)
      && tuple == term.of()) return term;
    return new CallTerm.Access(tuple, term.ref(), term.sortArgs(), structArgs, args);
  }
}
