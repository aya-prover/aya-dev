// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.immutable.ImmutableMap;
import kala.tuple.Tuple;
import org.aya.core.term.*;
import org.aya.generic.Arg;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;

/**
 * @author ice1000
 */
public interface TermFixpoint<P> extends Term.Visitor<P, @NotNull Term> {
  @Override default @NotNull Term visitHole(@NotNull CallTerm.Hole term, P p) {
    var contextArgs = term.contextArgs().map(arg -> visitArg(arg, p));
    var args = term.args().map(arg -> visitArg(arg, p));
    if (ulift() == 0
      && term.contextArgs().sameElements(contextArgs, true)
      && term.args().sameElements(args, true)) return term;
    return new CallTerm.Hole(term.ref(), ulift() + term.ulift(), contextArgs, args);
  }
  @Override
  default @NotNull Term visitFieldRef(@NotNull RefTerm.Field term, P p) {
    return term;
  }

  default int ulift() {
    return 0;
  }

  @Override default @NotNull Term visitDataCall(@NotNull CallTerm.Data dataCall, P p) {
    var args = dataCall.args().map(arg -> visitArg(arg, p));
    if (ulift() == 0 && dataCall.args().sameElements(args, true)) return dataCall;
    return new CallTerm.Data(dataCall.ref(), ulift() + dataCall.ulift(), args);
  }

  @Override default @NotNull ErrorTerm visitError(@NotNull ErrorTerm term, P p) {
    return term;
  }

  @Override default @NotNull Term visitMetaPat(RefTerm.@NotNull MetaPat metaPat, P p) {
    return metaPat;
  }

  @Override
  @NotNull
  default Term visitInterval(FormTerm.@NotNull Interval interval, P p) {
    return interval;
  }

  @Override @NotNull default Term visitEnd(PrimTerm.@NotNull End end, P p) {
    return end;
  }
  @Override @NotNull default Term visitStr(PrimTerm.@NotNull Str str, P p) {
    return str;
  }

  @Override default @NotNull Term visitConCall(@NotNull CallTerm.Con conCall, P p) {
    var dataArgs = conCall.head().dataArgs().map(arg -> visitArg(arg, p));
    var conArgs = conCall.conArgs().map(arg -> visitArg(arg, p));
    if (ulift() == 0
      && conCall.head().dataArgs().sameElements(dataArgs, true)
      && conCall.conArgs().sameElements(conArgs, true)) return conCall;
    var head = new CallTerm.ConHead(conCall.head().dataRef(), conCall.head().ref(),
      ulift() + conCall.ulift(), dataArgs);
    return new CallTerm.Con(head, conArgs);
  }

  @Override default @NotNull Term visitStructCall(@NotNull StructCall structCall, P p) {
    var params = structCall.params().map(x -> Tuple.of(x._1, visitArg(x._2, p)));
    if (ulift() == 0 && structCall.params().sameElements(params, true)) return structCall;
    return new StructCall(structCall.ref(), ulift() + structCall.ulift(), params);
  }

  private <T> T visitParameterized(
    @NotNull Term.Param theParam, @NotNull Term theBody, @NotNull P p, @NotNull T original,
    @NotNull BiFunction<Term.@NotNull Param, @NotNull Term, T> callback
  ) {
    var param = new Term.Param(theParam, theParam.type().accept(this, p));
    var body = theBody.accept(this, p);
    if (param.type() == theParam.type() && body == theBody) return original;
    return callback.apply(param, body);
  }

  @Override default @NotNull Term visitLam(@NotNull IntroTerm.Lambda term, P p) {
    return visitParameterized(term.param(), term.body(), p, term, IntroTerm.Lambda::new);
  }

  @Override default @NotNull Term visitUniv(@NotNull FormTerm.Univ term, P p) {
    if (ulift() == 0) return term;
    else return new FormTerm.Univ(ulift() + term.lift());
  }

  @Override default @NotNull Term visitPi(@NotNull FormTerm.Pi term, P p) {
    return visitParameterized(term.param(), term.body(), p, term, FormTerm.Pi::new);
  }

  @Override default @NotNull Term visitSigma(@NotNull FormTerm.Sigma term, P p) {
    var params = term.params().map(param ->
      new Term.Param(param, param.type().accept(this, p)));
    if (params.sameElements(term.params(), true)) return term;
    return new FormTerm.Sigma(params);
  }

  @Override default @NotNull Term visitRef(@NotNull RefTerm term, P p) {
    return term;
  }

  @Override default @NotNull Term visitSelf(@NotNull RefTerm.Self term, P p) {
    return term;
  }

  default @NotNull Arg<Term> visitArg(@NotNull Arg<Term> arg, P p) {
    var term = arg.term().accept(this, p);
    if (term == arg.term()) return arg;
    return new Arg<>(term, arg.explicit());
  }

  @Override default @NotNull Term visitApp(@NotNull ElimTerm.App term, P p) {
    var function = term.of().accept(this, p);
    var arg = visitArg(term.arg(), p);
    if (function == term.of() && arg == term.arg()) return term;
    return CallTerm.make(function, arg);
  }

  @Override default @NotNull Term visitFnCall(CallTerm.@NotNull Fn fnCall, P p) {
    var args = fnCall.args().map(arg -> visitArg(arg, p));
    if (ulift() == 0 && fnCall.args().sameElements(args, true)) return fnCall;
    return new CallTerm.Fn(fnCall.ref(), ulift() + fnCall.ulift(), args);
  }

  @Override default @NotNull Term visitPrimCall(CallTerm.@NotNull Prim prim, P p) {
    var args = prim.args().map(arg -> visitArg(arg, p));
    if (ulift() == 0 && prim.args().sameElements(args, true)) return prim;
    return new CallTerm.Prim(prim.ref(), ulift() + prim.ulift(), args);
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
    // Supposed to succeed
    return new IntroTerm.New((StructCall) struct.struct().accept(this, p), items);
  }

  @Override default @NotNull Term visitProj(@NotNull ElimTerm.Proj term, P p) {
    var tuple = term.of().accept(this, p);
    if (ulift() == 0 && tuple == term.of()) return term;
    return new ElimTerm.Proj(tuple, term.ix());
  }

  @Override default @NotNull Term visitAccess(@NotNull CallTerm.Access term, P p) {
    var tuple = term.of().accept(this, p);
    var args = term.fieldArgs().map(arg -> visitArg(arg, p));
    var structArgs = term.structArgs().map(arg -> visitArg(arg, p));
    if (term.fieldArgs().sameElements(args, true)
      && term.structArgs().sameElements(structArgs, true)
      && tuple == term.of()) return term;
    return new CallTerm.Access(tuple, term.ref(), structArgs, args);
  }

  @Override default @NotNull Term visitShapedLit(LitTerm.@NotNull ShapedInt shaped, P p) {
    var type = shaped.type().accept(this, p);
    if (type == shaped.type()) return shaped;
    return new LitTerm.ShapedInt(shaped.repr(), shaped.shape(), type);
  }
}
