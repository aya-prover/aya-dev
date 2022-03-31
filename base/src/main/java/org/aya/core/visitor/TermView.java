// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.immutable.ImmutableMap;
import kala.tuple.Tuple;
import org.aya.core.term.*;
import org.aya.generic.Arg;

import java.util.function.Function;

/// Generic Term traversal with pre-order and post-order hooks
public interface TermView {
  Term initial();

  default Term pre(Term term) {
    return term;
  }

  default Term post(Term term) {
    return term;
  }

  private Term commit(Term term) {
    return post(traverse(pre(term)));
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

  private Term traverse(Term term) {
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
        // TODO: Should we use `yield CallTerm.make(function, arg);` instead?
        yield new ElimTerm.App(function, app.ulift(), arg);
      }
      case ElimTerm.Proj proj -> {
        var tuple = commit(proj.of());
        if (tuple == proj.of()) yield proj;
        yield new ElimTerm.Proj(tuple, proj.ulift(), proj.ix());
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
        yield new CallTerm.Access(struct, access.ref(), access.ulift(), structArgs, fieldArgs);
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

  default Term commit() {
    return commit(initial());
  }

  default TermView lift(int shift) {
    return new TermLift(this, shift);
  }

  default TermView subst(Substituter.TermSubst subst) {
    return new TermSubst(this, subst);
  }

  default TermView postMap(Function<Term, Term> f) {
    return new TermMap(this, t -> t, f);
  }

  default TermView preMap(Function<Term, Term> f) {
    return new TermMap(this, f, t -> t);
  }

  default TermView map(Function<Term, Term> pre, Function<Term, Term> post) {
    return new TermMap(this, pre, post);
  }
}
