// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
import org.aya.api.util.Arg;
import org.aya.core.Matching;
import org.aya.core.pat.Pat;
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

  private Value makeLam(SeqView<Term.Param> telescope, Term body, Environment env) {
    var param = telescope.firstOrNull();
    if (param != null) {
      var p = eval(param, env);
      return new IntroValue.Lam(p, arg -> makeLam(telescope.drop(1), body, env.added(p.ref(), arg)));
    }
    return body.accept(this, env);
  }

  private record Matchy(@NotNull Value value, @NotNull Term.Param param) {
  }

  /**
   * Attempt to match {@code value} against certain {@code pattern}.
   * The {@code buffer} will be filled with extracted binding sites in the pattern.
   *
   * @return if match succeeds
   */
  private boolean matchPat(@NotNull Pat pattern, @NotNull Value value, @NotNull Buffer<Matchy> out) {
    return switch (pattern) {
      case Pat.Bind bind -> {
        out.append(new Matchy(value, new Term.Param(bind.as(), bind.type(), bind.explicit())));
        yield true;
      }
      case Pat.Tuple tuple -> {
        var tailValue = value;
        for (var pat : tuple.pats()) {
          if (!(tailValue instanceof IntroValue.Pair pair)) yield false;
          if (!matchPat(pat, pair.left(), out)) yield false;
          tailValue = pair.right();
        }
        if (tuple.as() != null) {
          out.append(new Matchy(value, new Term.Param(tuple.as(), tuple.type(), tuple.explicit())));
        }
        yield true;
      }
      // TODO: Handle prim/ctor call
      case Pat.Prim prim -> false;
      case Pat.Ctor ctor -> false;
      case Pat.Absurd ignore -> false;
    };
  }

  /**
   * Helper function to construct the lambda value for a function defined by pattern matching.
   */
  private Value matchingsHelper(SeqView<Term.Param> telescope, SeqView<Matching> matchings, SeqView<Value> values, Environment env) {
    var param = telescope.firstOrNull();
    if (param != null) {
      var p = eval(param, env);
      return new IntroValue.Lam(p, arg -> matchingsHelper(telescope.drop(1), matchings, values.appended(arg), env.added(p.ref(), arg)));
    }
    for (var matching : matchings) {
      var out = Buffer.<Matchy>create();
      var matches = matching.patterns().zip(values)
        .allMatch(tup -> matchPat(tup._1, tup._2, out));
      if (!matches) continue;
      var spine = out.view()
        .<Value.Segment>map((m) -> new Value.Segment.Apply(m.value, m.param.explicit()))
        .toImmutableSeq();
      return makeLam(out.view().map(Matchy::param), matching.body(), env).elim(spine);
    }
    return null;
  }

  private Value makeLam(SeqView<Term.Param> telescope, SeqView<Matching> matchings, Environment env) {
    return matchingsHelper(telescope, matchings, SeqView.empty(), env);
  }

  @Override
  public Value visitRef(@NotNull RefTerm ref, Environment env) {
    return env.lookup(ref.var());
  }

  @Override
  public Value visitLam(IntroTerm.@NotNull Lambda lambda, Environment env) {
    var param = eval(lambda.param(), env);
    return new IntroValue.Lam(param, x -> lambda.body().accept(this, env.added(param.ref(), x)));
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
    var sig = new FormTerm.Sigma(params.drop(1));
    return new FormValue.Sigma(param, x -> sig.accept(this, env.added(param.ref(), x)));
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
      body -> makeLam(fnDef.telescope.view(), body, env),
      matchings -> makeLam(fnDef.telescope.view(), matchings.view(), env)
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
    return items.foldRight(new IntroValue.TT(), IntroValue.Pair::new);
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
