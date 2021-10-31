// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.util.Arg;
import org.aya.core.term.*;
import org.aya.generic.Environment;
import org.aya.value.FormValue;
import org.aya.value.IntroValue;
import org.aya.value.RefValue;
import org.aya.value.Value;
import org.jetbrains.annotations.NotNull;

public class Evaluator implements Term.Visitor<Environment, Value> {
  private Value.Param eval(Term.Param param, Environment env) {
    return new Value.Param(param.ref(), param.type().accept(this, env), param.explicit());
  }

  private Value.Arg eval(Arg<Term> arg, Environment env) {
    return new Value.Arg(arg.term().accept(this, env), arg.explicit());
  }

  private Value makeLam(ImmutableSeq<Term.Param> telescope, Term body, Environment env) {
    var param = telescope.firstOrNull();
    if (param != null) {
      var p = eval(param, env);
      return new IntroValue.Lambda(p, arg -> makeLam(telescope.drop(1), body, env.added(p.ref(), arg)));
    } else {
      return body.accept(this, env);
    }
  }

  @Override
  public Value visitRef(@NotNull RefTerm ref, Environment env) {
    return env.lookup(ref.var());
  }

  @Override
  public Value visitLam(IntroTerm.@NotNull Lambda lambda, Environment env) {
    var param = eval(lambda.param(), env);
    return new IntroValue.Lambda(param, x -> lambda.body().accept(this, env.added(param.ref(), x)));
  }

  @Override
  public Value visitPi(FormTerm.@NotNull Pi pi, Environment env) {
    var param = eval(pi.param(), env);
    return new FormValue.Pi(param, x -> pi.body().accept(this, env.added(param.ref(), x)));
  }

  @Override
  public Value visitSigma(FormTerm.@NotNull Sigma sigma, Environment env) {
    var params = sigma.params();
    if (params.isEmpty()) {
      return new FormValue.Unit();
    }
    var param = eval(params.first(), env);
    params = params.drop(1);
    if (params.isEmpty()) {
      return param.type();
    }
    var sig = new FormTerm.Sigma(params);
    return new FormValue.Sig(param, x -> sig.accept(this, env.added(param.ref(), x)));
  }

  @Override
  public Value visitUniv(FormTerm.@NotNull Univ univ, Environment env) {
    return new FormValue.Univ(univ.sort());
  }

  @Override
  public Value visitApp(ElimTerm.@NotNull App app, Environment env) {
    var func = app.of().accept(this, env);
    return func.apply(eval(app.arg(), env));
  }

  @Override
  public Value visitFnCall(@NotNull CallTerm.Fn fnCall, Environment env) {
    var fnDef = fnCall.ref().core;
    var args = fnCall.args().map(arg -> eval(arg, env));
    ImmutableSeq<Value.Segment> spine = args.map(Value.Segment.Apply::new);
    return new RefValue.Flex(fnCall.ref(), spine, () -> fnDef.body.fold(
      body -> makeLam(fnDef.telescope, body, env),
      matchings -> null
    ).elim(spine));
  }

  @Override
  public Value visitDataCall(@NotNull CallTerm.Data dataCall, Environment env) {
    return null;
  }

  @Override
  public Value visitConCall(@NotNull CallTerm.Con conCall, Environment env) {
    return null;
  }

  @Override
  public Value visitStructCall(@NotNull CallTerm.Struct structCall, Environment env) {
    return null;
  }

  @Override
  public Value visitPrimCall(CallTerm.@NotNull Prim prim, Environment env) {
    return null;
  }

  @Override
  public Value visitTup(IntroTerm.@NotNull Tuple tuple, Environment env) {
    var items = tuple.items().map(item -> item.accept(this, env));
    if (items.isEmpty()) {
      return new FormValue.Unit();
    }
    var first = items.first();
    return items.drop(1).foldRight(first, IntroValue.Pair::new);
  }

  @Override
  public Value visitNew(IntroTerm.@NotNull New newTerm, Environment env) {
    return null;
  }

  @Override
  public Value visitProj(ElimTerm.@NotNull Proj proj, Environment env) {
    var tup = proj.of().accept(this, env);
    var spine = ImmutableSeq
      .fill(proj.ix(), (Value.Segment) new Value.Segment.ProjR())
      .prepended(new Value.Segment.ProjL());
    return tup.elim(spine);
  }

  @Override
  public Value visitAccess(CallTerm.@NotNull Access access, Environment env) {
    return null;
  }

  @Override
  public Value visitHole(CallTerm.@NotNull Hole hole, Environment env) {
    return null;
  }

  @Override
  public Value visitFieldRef(RefTerm.@NotNull Field field, Environment env) {
    return null;
  }

  @Override
  public Value visitError(@NotNull ErrorTerm error, Environment env) {
    return null;
  }
}
