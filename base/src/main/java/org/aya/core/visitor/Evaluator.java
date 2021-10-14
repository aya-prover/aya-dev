// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import org.aya.core.term.*;
import org.aya.generic.Environment;
import org.aya.value.*;
import org.jetbrains.annotations.NotNull;

public class Evaluator implements Term.Visitor<Environment, Value> {
  @Override
  public Value visitRef(@NotNull RefTerm ref, Environment env) {
    return env.lookup(ref.var());
  }

  @Override
  public Value visitLam(IntroTerm.@NotNull Lambda lambda, Environment env) {
    var param = lambda.param();
    var paramType = param.type().accept(this, env);
    return new IntroValue.Lambda(
      new Value.Param(param.ref(), paramType, param.explicit()),
      x -> lambda.body().accept(this, env.added(param.ref(), x))
    );
  }

  @Override
  public Value visitPi(FormTerm.@NotNull Pi pi, Environment env) {
    var param = pi.param();
    var paramType = param.type().accept(this, env);
    return new FormValue.Pi(
      new Value.Param(param.ref(), paramType, param.explicit()),
      x -> pi.body().accept(this, env.added(param.ref(), x))
    );
  }

  @Override
  public Value visitSigma(FormTerm.@NotNull Sigma sigma, Environment env) {
    var params = sigma.params().view();
    if (params.isEmpty()) {
      return new FormValue.Unit();
    }
    var param = params.first();
    var paramType = param.type().accept(this, env);
    var finalParams = params.drop(1);
    if (finalParams.isEmpty()) {
      return paramType;
    }
    return new FormValue.Sig(
      new Value.Param(param.ref(), paramType, param.explicit()),
      x -> new FormTerm.Sigma(finalParams.toImmutableSeq()).accept(this, env.added(param.ref(), x))
    );
  }

  @Override
  public Value visitUniv(FormTerm.@NotNull Univ univ, Environment env) {
    return new FormValue.Univ(univ.sort());
  }

  @Override
  public Value visitApp(ElimTerm.@NotNull App app, Environment env) {
    var func = app.of().accept(this, env);
    var arg = app.arg().term().accept(this, env);
    if (func instanceof IntroValue.Lambda lam) {
      // TODO: Handle "licity"
      return lam.func().apply(arg);
    } if (func instanceof RefValue.Neu v) {
      var spine = v.spine().appended(new RefValue.Segment.Apply(arg, app.arg().explicit()));
      return new RefValue.Neu(v.var(), spine);
    }
    return null;
  }

  @Override
  public Value visitFnCall(@NotNull CallTerm.Fn fnCall, Environment env) {
    return null;
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
    return null;
  }

  @Override
  public Value visitNew(IntroTerm.@NotNull New newTerm, Environment env) {
    return null;
  }

  @Override
  public Value visitProj(ElimTerm.@NotNull Proj proj, Environment env) {
    return null;
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
