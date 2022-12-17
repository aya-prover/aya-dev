// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.value;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import org.aya.core.Matching;
import org.aya.core.Meta;
import org.aya.core.def.FieldDef;
import org.aya.core.pat.Pat;
import org.aya.core.term.*;
import org.aya.generic.Arg;
import org.aya.generic.Environment;
import org.jetbrains.annotations.NotNull;

public record Evaluator(@NotNull MutableMap<Meta, Value> metaCtx) {
  private Value.Param eval(Environment env, Term.Param param) {
    return new Value.Param(param.ref(), eval(env, param.type()), param.explicit());
  }

  private Value.Arg eval(Environment env, Arg<Term> arg) {
    return new Value.Arg(eval(env, arg.term()), arg.explicit());
  }

  private Value makeLam(Environment env, SeqView<Term.Param> telescope, Term body) {
    var param = telescope.firstOrNull();
    if (param != null) {
      var p = eval(env, param);
      return new IntroValue.Lam(p, arg -> makeLam(env.added(p.ref(), arg), telescope.drop(1), body));
    }
    return eval(env, body);
  }

  /**
   * Attempt to match {@code value} against certain {@code pattern}.
   * The {@code buffer} will be filled with extracted binding sites in the pattern.
   *
   * @return if match succeeds
   */
  private boolean matchPat(@NotNull Pat pattern, @NotNull Value value, @NotNull DynamicSeq<Matchy> out) {
    return switch (pattern) {
      case Pat.Bind bind -> {
        out.append(new Matchy(value, new Term.Param(bind.bind(), bind.type(), bind.explicit())));
        yield true;
      }
      case Pat.Tuple tuple -> {
        var tailValue = value;
        for (var pat : tuple.pats()) {
          if (!(tailValue instanceof IntroValue.Pair pair)) yield false;
          if (!matchPat(pat, pair.left(), out)) yield false;
          tailValue = pair.right();
        }
        yield true;
      }
      // TODO: Handle prim call and meta
      case Pat.Prim prim -> false;
      case Pat.Ctor ctor -> {
        if (!(value instanceof IntroValue.Ctor con)) yield false;
        if (ctor.ref().core != con.def()) yield false;
        // TODO: unify the data types?
        yield ctor.params().zip(con.args()).allMatch(pair -> matchPat(pair._1, pair._2.value(), out));
      }
      case Pat.Meta meta -> false;
      case Pat.Absurd ignore -> false;
    };
  }

  /**
   * Helper function to construct the lambda value for a function defined by pattern matching.
   */
  private Value matchingsHelper(Environment env, SeqView<Term.Param> telescope, SeqView<Matching> matchings, SeqView<Value> values) {
    var param = telescope.firstOrNull();
    if (param != null) {
      var p = eval(env, param);
      return new IntroValue.Lam(p, arg -> matchingsHelper(env.added(p.ref(), arg), telescope.drop(1), matchings, values.appended(arg)));
    }
    for (var matching : matchings) {
      var out = DynamicSeq.<Matchy>create();
      var matches = matching.patterns().zip(values)
        .allMatch(tup -> matchPat(tup._1, tup._2, out));
      if (!matches) continue;
      var spine = out.view()
        .<Value.Segment>map((m) -> new Value.Segment.Apply(m.value, m.param.explicit()))
        .toImmutableSeq();
      return makeLam(env, out.view().map(Matchy::param), matching.body()).elim(spine);
    }
    return null;
  }

  private Value makeLam(Environment env, SeqView<Term.Param> telescope, SeqView<Matching> matchings) {
    return matchingsHelper(env, telescope, matchings, SeqView.empty());
  }

  Value eval(Environment env, Term term) {
    return switch (term) {
      case FormTerm.Sigma sigma -> {
        var params = sigma.params();
        if (params.isEmpty()) {
          yield new FormValue.Unit();
        }
        var param = eval(env, params.first());
        var sig = new FormTerm.Sigma(params.drop(1));
        yield new FormValue.Sigma(param, x -> eval(env.added(param.ref(), x), sig));
      }
      case FormTerm.Pi pi -> {
        var param = eval(env, pi.param());
        yield new FormValue.Pi(param, x -> eval(env.added(param.ref(), x), pi.body()));
      }
      case CallTerm.Data data -> {
        var def = data.ref().core;
        var args = data.args().map(arg -> eval(env, arg));
        yield new FormValue.Data(def, args);
      }
      case CallTerm.Struct struct -> {
        var def = struct.ref().core;
        var args = struct.args().map(arg -> eval(env, arg));
        yield new FormValue.Struct(def, args);
      }
      case FormTerm.Univ univ -> new FormValue.Univ(univ.sort());
      case IntroTerm.Tuple tuple -> {
        var items = tuple.items().map(item -> eval(env, item));
        yield items.foldRight(new IntroValue.TT(), IntroValue.Pair::new);
      }
      case IntroTerm.Lambda lambda -> {
        var param = eval(env, lambda.param());
        yield new IntroValue.Lam(param, x -> eval(env.added(param.ref(), x), lambda.body()));
      }
      case CallTerm.Con con -> {
        var def = con.ref().core;
        var args = con.conArgs().map(arg -> eval(env, arg));
        var dataDef = con.head().dataRef().core;
        var dataArgs = con.head().dataArgs().map(arg -> eval(env, arg));
        var data = new FormValue.Data(dataDef, dataArgs);
        yield new IntroValue.Ctor(def, args, data);
      }
      case IntroTerm.New newTerm -> {
        var struct = (FormValue.Struct) eval(env, newTerm.struct());
        var params = newTerm.params().view()
          .map((fieldVar, fieldVal) -> Tuple.of(fieldVar.core, eval(env, fieldVal))).<FieldDef, Value>toImmutableMap();
        yield new IntroValue.New(params, struct);
      }
      case ElimTerm.Proj proj -> {
        var tup = eval(env, proj.of());
        var spine = ImmutableSeq
          .fill(proj.ix(), (Value.Segment) new Value.Segment.ProjR())
          .prepended(new Value.Segment.ProjL());
        yield tup.elim(spine);
      }
      case ElimTerm.App app -> {
        var func = eval(env, app.of());
        yield func.apply(eval(env, app.arg()));
      }
      case CallTerm.Fn fnCall -> {
        var fnDef = fnCall.ref().core;
        var args = fnCall.args().map(arg -> eval(env, arg));
        ImmutableSeq<Value.Segment> spine = args.map(Value.Segment.Apply::new);
        yield new RefValue.Flex(fnCall.ref(), spine, () -> fnDef.body.fold(
          body -> makeLam(env, fnDef.telescope.view(), body),
          matchings -> makeLam(env, fnDef.telescope.view(), matchings.view())
        ).elim(spine));
      }
      case CallTerm.Access access -> {
        var struct = eval(env, access);
        var fieldApps = access.fieldArgs().map(arg -> (Value.Segment) new Value.Segment.Apply(eval(env, arg)));
        yield struct.access(access.ref().core).elim(fieldApps);
      }
      case CallTerm.Hole hole -> {
        var result = metaCtx.get(hole.ref());
        if (result != null) {
          yield result;
        } else {
          yield new RefValue.Flex(hole.ref(), () -> metaCtx.get(hole.ref()));
        }
      }
      case RefTerm ref -> env.lookup(ref.var());
      default -> null;
    };
  }

  private record Matchy(@NotNull Value value, @NotNull Term.Param param) {}
}
