// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableMap;
import kala.collection.mutable.MutableList;
import kala.control.Either;
import kala.tuple.Tuple;
import org.aya.core.term.*;
import org.aya.generic.Arg;

/* Highly experimental support for skipping subtree traversal.
 * We now model the travel plan as a list of hooks,
 * where each hook is a pair of function (pre-hook and post-hook).
 * To signal a skip, the pre-hook returns a `Either<Term, Term>` instead of `Term`
 * If the processed value is of the left case, we proceed normally,
 * and for the right case, we will unregister the hook in all later subtree traversal.
 */
public record Traveller(Seq<Hook> hooks) {
  public Term commit(Term term) {
    if (hooks.isEmpty()) return term;
    MutableList<Hook> newHooks = MutableList.create();
    var t1 = hooks.foldLeft(term, (t, hook) -> hook.prep(t).fold(cont -> {
      newHooks.append(hook);
      return cont;
    }, ret -> ret));
    return hooks.foldRight(new Traveller(newHooks).traverse(t1), Hook::post);
  }

  private Term.Param commit(Term.Param param) {
    var type = commit(param.type());
    if (type == param.type()) return param;
    return new Term.Param(param, type);
  }

  private Arg<Term> commit(Arg<Term> arg) {
    var term = commit(arg.term());
    if (term == arg.term()) return arg;
    return new Arg<>(term, arg.explicit());
  }

  private CallTerm.ConHead commit(CallTerm.ConHead head) {
    var args = head.dataArgs().map(this::commit);
    if (args.sameElements(head.dataArgs(), true)) return head;
    return new CallTerm.ConHead(head.dataRef(), head.ref(), head.ulift(), args);
  }

  public Term traverse(Term term) {
    return switch (term) {
      case FormTerm.Pi pi -> {
        var param = commit(pi.param());
        var body = commit(pi.body());
        if (param == pi.param() && body == pi.body()) yield pi;
        yield new FormTerm.Pi(param, body);
      }
      case FormTerm.Sigma sigma -> {
        var params = sigma.params().map(this::commit);
        if (params.sameElements(sigma.params(), true)) yield sigma;
        yield new FormTerm.Sigma(params);
      }
      case FormTerm.Univ univ -> univ;
      case IntroTerm.Lambda lambda -> {
        var param = commit(lambda.param());
        var body = commit(lambda.body());
        if (param == lambda.param() && body == lambda.body()) yield lambda;
        yield new IntroTerm.Lambda(param, body);
      }
      case IntroTerm.Tuple tuple -> {
        var items = tuple.items().map(this::commit);
        if (items.sameElements(tuple.items(), true)) yield tuple;
        yield new IntroTerm.Tuple(items);
      }
      case IntroTerm.New neu -> {
        var struct = commit(neu.struct());
        var fields = ImmutableMap.from(neu.params().view().map((k, v) -> Tuple.of(k, commit(v))));
        if (struct == neu.struct() && fields.valuesView().sameElements(neu.params().valuesView())) yield neu;
        yield new IntroTerm.New((CallTerm.Struct) struct, fields);
      }
      case ElimTerm.App app -> {
        var function = commit(app.of());
        var arg = commit(app.arg());
        if (function == app.of() && arg == app.arg()) yield app;
        yield CallTerm.make(function, arg);
      }
      case ElimTerm.Proj proj -> {
        var tuple = commit(proj.of());
        if (tuple == proj.of()) yield proj;
        yield new ElimTerm.Proj(tuple, proj.ix());
      }
      case CallTerm.Struct struct -> {
        var args = struct.args().map(this::commit);
        if (args.sameElements(struct.args(), true)) yield struct;
        yield new CallTerm.Struct(struct.ref(), struct.ulift(), args);
      }
      case CallTerm.Data data -> {
        var args = data.args().map(this::commit);
        if (args.sameElements(data.args(), true)) yield data;
        yield new CallTerm.Data(data.ref(), data.ulift(), args);
      }
      case CallTerm.Con con -> {
        var head = commit(con.head());
        var args = con.conArgs().map(this::commit);
        if (head == con.head() && args.sameElements(con.conArgs(), true)) yield con;
        yield new CallTerm.Con(head, args);
      }
      case CallTerm.Fn fn -> {
        var args = fn.args().map(this::commit);
        if (args.sameElements(fn.args(), true)) yield fn;
        yield new CallTerm.Fn(fn.ref(), fn.ulift(), args);
      }
      case CallTerm.Access access -> {
        var struct = commit(access.of());
        var structArgs = access.structArgs().map(this::commit);
        var fieldArgs = access.fieldArgs().map(this::commit);
        if (struct == access.of()
          && structArgs.sameElements(access.structArgs(), true)
          && fieldArgs.sameElements(access.fieldArgs(), true))
          yield access;
        yield new CallTerm.Access(struct, access.ref(), structArgs, fieldArgs);
      }
      case CallTerm.Prim prim -> {
        var args = prim.args().map(this::commit);
        if (args.sameElements(prim.args(), true)) yield prim;
        yield new CallTerm.Prim(prim.ref(), prim.ulift(), args);
      }
      case CallTerm.Hole hole -> {
        var contextArgs = hole.contextArgs().map(this::commit);
        var args = hole.args().map(this::commit);
        if (contextArgs.sameElements(hole.contextArgs(), true) && args.sameElements(hole.args(), true)) yield hole;
        yield new CallTerm.Hole(hole.ref(), hole.ulift(), contextArgs, args);
      }
      case RefTerm.Field field -> field;
      case RefTerm ref -> ref;
      case RefTerm.MetaPat metaPat -> metaPat;
      case ErrorTerm error -> error;
    };
  }
}

interface Hook {
  default Either<Term, Term> prep(Term term) {
    return Either.left(pre(term));
  }

  default Term pre(Term term) {
    return term;
  }

  default Term post(Term term) {
    return term;
  }
}
