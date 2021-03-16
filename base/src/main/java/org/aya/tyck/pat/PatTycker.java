// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.pat;

import org.aya.api.error.IgnoringReporter;
import org.aya.api.error.Reporter;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.Pattern;
import org.aya.concrete.visitor.ExprRefSubst;
import org.aya.core.def.DataDef;
import org.aya.core.def.Def;
import org.aya.core.pat.Pat;
import org.aya.core.term.AppTerm;
import org.aya.core.term.SigmaTerm;
import org.aya.core.term.Term;
import org.aya.core.term.UnivTerm;
import org.aya.ref.LocalVar;
import org.aya.tyck.ExprTycker;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.collection.mutable.MutableHashSet;
import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.tuple.Tuple2;
import org.glavo.kala.tuple.Unit;
import org.glavo.kala.value.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author ice1000
 */
public final class PatTycker implements Pattern.Visitor<Term, Pat> {
  private final @NotNull ExprTycker exprTycker;
  private final @NotNull ExprRefSubst subst;

  public PatTycker(@NotNull ExprTycker exprTycker) {
    this.exprTycker = exprTycker;
    subst = new ExprRefSubst(exprTycker.metaContext.reporter(), MutableHashMap.of(), MutableHashSet.of());
  }

  public @NotNull Tuple2<@NotNull Term, @NotNull ImmutableSeq<Pat.PrototypeClause>>
  elabClause(@NotNull ImmutableSeq<Pattern.@NotNull Clause> clauses, Ref<Def.@NotNull Signature> signature) {
    var res = clauses.map(clause -> {
      var elabClause = visitMatch(clause, signature.value);
      signature.value = signature.value.mapTerm(elabClause._1);
      return elabClause._2;
    });
    return Tuple.of(signature.value.result(), res);
  }

  public Tuple2<@NotNull Term, Pat.PrototypeClause> visitMatch(Pattern.@NotNull Clause match, Def.Signature signature) {
    var sig = new Ref<>(signature);
    subst.clear();
    var recover = MutableHashMap.from(exprTycker.localCtx);
    var patterns = visitPatterns(sig, match.patterns());
    var result = match.expr()
      .map(e -> e.accept(subst, Unit.unit()))
      .map(e -> exprTycker.checkExpr(e, sig.value.result()));
    exprTycker.localCtx.clear();
    exprTycker.localCtx.putAll(recover);
    var type = result.map(ExprTycker.Result::type).getOrDefault(sig.value.result());
    return Tuple.of(type, new Pat.PrototypeClause(patterns, result.map(ExprTycker.Result::wellTyped)));
  }

  private @NotNull ImmutableSeq<Pat> visitPatterns(Ref<Def.Signature> sig, SeqLike<Pattern> stream) {
    var results = Buffer.<Pat>of();
    stream.forEach(pat -> {
      var param = sig.value.param().first();
      while (param.explicit() != pat.explicit()) if (pat.explicit()) {
        var bind = new Pat.Bind(false, new LocalVar(param.ref().name()), param.type());
        results.append(bind);
        sig.value = sig.value.inst(bind.toTerm());
        param = sig.value.param().first();
      } else {
        // TODO[ice]: unexpected implicit pattern
        throw new ExprTycker.TyckerException();
      }
      var res = pat.accept(this, param.type());
      sig.value = sig.value.inst(res.toTerm());
      results.append(res);
    });
    return results.toImmutableSeq();
  }

  @Override public Pat visitCalmFace(Pattern.@NotNull CalmFace face, Term t) {
    throw new UnsupportedOperationException();
  }

  @Override public Pat visitNumber(Pattern.@NotNull Number number, Term t) {
    throw new UnsupportedOperationException();
  }

  @Override public Pat visitTuple(Pattern.@NotNull Tuple tuple, Term t) {
    exprTycker.localCtx.put(tuple.as(), t);
    if (!(t instanceof SigmaTerm sigma)) {
      // TODO[ice]: requires pretty printing patterns
      throw new ExprTycker.TyckerException();
    }
    // sig.result is a dummy term
    var sig = new Def.Signature(
      sigma.params().appended(new Term.Param(new LocalVar("_"), sigma.body(), true)),
      UnivTerm.OMEGA);
    exprTycker.localCtx.put(tuple.as(), sigma);
    return new Pat.Tuple(tuple.explicit(),
      visitPatterns(new Ref<>(sig), tuple.patterns()), tuple.as(), sigma);
  }

  @Override public Pat visitBind(Pattern.@NotNull Bind bind, Term t) {
    var v = bind.bind();
    var selected = selectCtor(t, v.name(), IgnoringReporter.INSTANCE);
    if (selected == null) {
      exprTycker.localCtx.put(v, t);
      return new Pat.Bind(bind.explicit(), v, t);
    }
    if (!selected._2.conTelescope().isEmpty()) {
      // TODO: error report: not enough parameters bind
      throw new ExprTycker.TyckerException();
    }
    var value = bind.resolved().value;
    if (value != null) subst.good().putIfAbsent(v, value);
    else subst.bad().add(v);
    return new Pat.Ctor(bind.explicit(), selected._2.ref(), ImmutableSeq.of(), null, selected._1);
  }

  @Override public Pat visitCtor(Pattern.@NotNull Ctor ctor, Term param) {
    var realCtor = selectCtor(param, ctor.name(), subst.reporter());
    if (realCtor == null) throw new ExprTycker.TyckerException();
    var sig = new Ref<>(new Def.Signature(realCtor._2.conTelescope(), realCtor._2.result()));
    var patterns = visitPatterns(sig, ctor.params());
    return new Pat.Ctor(ctor.explicit(), realCtor._2.ref(), patterns, ctor.as(), realCtor._1);
  }

  private @Nullable Tuple2<AppTerm.DataCall, DataDef.Ctor>
  selectCtor(Term param, @NotNull String name, @NotNull Reporter reporter) {
    if (!(param.normalize(NormalizeMode.WHNF) instanceof AppTerm.DataCall dataCall)) {
      // TODO[ice]: report error: splitting on non data
      return null;
    }
    var core = dataCall.dataRef().core;
    if (core == null) {
      // TODO[ice]: report error: not checked data
      return null;
    }
    var selected = core.ctors().find(c -> Objects.equals(c.ref().name(), name));
    if (selected.isEmpty()) {
      // TODO[ice]: report error: cannot find ctor of name
      return null;
    }
    return Tuple.of(dataCall, selected.get());
  }
}
