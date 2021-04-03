// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.pat;

import org.aya.api.error.IgnoringReporter;
import org.aya.api.error.Problem;
import org.aya.api.error.Reporter;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.Pattern;
import org.aya.concrete.visitor.ExprRefSubst;
import org.aya.core.def.DataDef;
import org.aya.core.def.Def;
import org.aya.core.def.PrimDef;
import org.aya.core.pat.Pat;
import org.aya.core.pat.PatMatcher;
import org.aya.core.term.CallTerm;
import org.aya.core.term.SigmaTerm;
import org.aya.core.term.Term;
import org.aya.core.term.UnivTerm;
import org.aya.core.visitor.Substituter;
import org.aya.core.visitor.Unfolder;
import org.aya.generic.GenericBuilder;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.error.*;
import org.aya.tyck.trace.Trace;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.collection.mutable.MutableHashSet;
import org.glavo.kala.collection.mutable.MutableMap;
import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.tuple.Tuple2;
import org.glavo.kala.tuple.Tuple3;
import org.glavo.kala.tuple.Unit;
import org.glavo.kala.value.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * @author ice1000
 */
public record PatTycker(
  @NotNull ExprTycker exprTycker,
  @NotNull ExprRefSubst subst,
  @Nullable Trace.Builder traceBuilder
) implements Pattern.Visitor<Term, Pat> {
  private void tracing(@NotNull Consumer<Trace.@NotNull Builder> consumer) {
    if (traceBuilder != null) consumer.accept(traceBuilder);
  }

  @Override
  public void traceEntrance(@NotNull Pattern pat, Term term) {
    tracing(builder -> builder.shift(new Trace.PatT(term, pat, pat.sourcePos())));
  }

  @Override
  public void traceExit(Pat pat, @NotNull Pattern pattern, Term term) {
    tracing(GenericBuilder::reduce);
  }

  public PatTycker(@NotNull ExprTycker exprTycker) {
    this(exprTycker, new ExprRefSubst(exprTycker.reporter, MutableHashMap.of(), MutableHashSet.of()), exprTycker.traceBuilder);
  }

  public @NotNull Tuple2<@NotNull Term, @NotNull ImmutableSeq<Pat.PrototypeClause>> elabClause(
    @NotNull ImmutableSeq<Pattern.@NotNull Clause> clauses,
    Ref<Def.@NotNull Signature> signature,
    @NotNull MutableMap<LocalVar, Term> cumulativeCtx
  ) {
    var res = clauses.mapIndexed((index, clause) -> {
      tracing(builder -> builder.shift(new Trace.LabelT(clause.sourcePos(), "clause " + (1 + index))));
      subst.clear();
      var elabClause = visitMatch(clause, signature.value, cumulativeCtx);
      tracing(GenericBuilder::reduce);
      return elabClause;
    });
    return Tuple.of(signature.value.result(), res);
  }

  @Override public Pat visitAbsurd(Pattern.@NotNull Absurd absurd, Term term) {
    var selection = selectCtor(term, null, subst.reporter(), absurd);
    if (selection != null) {
      subst.reporter().report(new PossiblePatError(absurd, selection._3));
      // This is actually not necessary. Do we want to delete it?
      throw new ExprTycker.TyckInterruptedException();
    }
    return new Pat.Absurd(absurd.explicit(), term);
  }

  public Pat.PrototypeClause visitMatch(
    Pattern.@NotNull Clause match, Def.@NotNull Signature signature,
    @NotNull MutableMap<LocalVar, Term> cumulativeCtx
  ) {
    var sig = new Ref<>(signature);
    exprTycker.localCtx = exprTycker.localCtx.derive();
    var patterns = visitPatterns(sig, match.patterns());
    var result = match.expr()
      .map(e -> e.accept(subst, Unit.unit()))
      .map(e -> exprTycker.checkExpr(e, sig.value.result()));
    var parent = exprTycker.localCtx.parent();
    assert parent != null;
    cumulativeCtx.putAll(exprTycker.localCtx.localMap());
    exprTycker.localCtx = parent;
    return new Pat.PrototypeClause(patterns, result.map(ExprTycker.Result::wellTyped));
  }

  public @NotNull ImmutableSeq<Pat> visitPatterns(Ref<Def.Signature> sig, SeqLike<Pattern> stream) {
    var results = Buffer.<Pat>of();
    stream.forEach(pat -> {
      if (sig.value.param().isEmpty()) {
        // TODO[ice]: report error
        throw new ExprTycker.TyckerException();
      }
      var param = sig.value.param().first();
      while (param.explicit() != pat.explicit()) if (pat.explicit()) {
        var bind = new Pat.Bind(false, new LocalVar(param.ref().name()), param.type());
        results.append(bind);
        sig.value = sig.value.inst(bind.toTerm());
        if (sig.value.param().isEmpty()) {
          // TODO[ice]: report error
          throw new ExprTycker.TyckerException();
        }
        param = sig.value.param().first();
      } else {
        // TODO[ice]: unexpected implicit pattern
        throw new ExprTycker.TyckerException();
      }
      Pat res = pat.accept(this, param.type());
      // if (param.type() instanceof CallTerm.Prim prim && prim.ref().core == PrimDef.INTERVAL) {
      //   res = new Pat.PrimPat(false, new LocalVar(param.ref().name()), param.type());
      // }
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
    if (tuple.as() != null) exprTycker.localCtx.put(tuple.as(), t);
    if (!(t instanceof SigmaTerm sigma)) {
      // TODO[ice]: requires pretty printing patterns
      throw new ExprTycker.TyckerException();
    }
    // sig.result is a dummy term
    var sig = new Def.Signature(
      ImmutableSeq.of(),
      sigma.params().appended(new Term.Param(new LocalVar("_"), sigma.body(), true)),
      UnivTerm.OMEGA);
    if (tuple.as() != null) exprTycker.localCtx.put(tuple.as(), sigma);
    return new Pat.Tuple(tuple.explicit(),
      visitPatterns(new Ref<>(sig), tuple.patterns()), tuple.as(), sigma);
  }

  @Override public Pat visitBind(Pattern.@NotNull Bind bind, Term t) {
    var v = bind.bind();
    var selected = selectCtor(t, v.name(), IgnoringReporter.INSTANCE, bind);
    if (t instanceof CallTerm.Prim prim && prim.ref().core == PrimDef.INTERVAL)
      for (var def : Seq.of(PrimDef.LEFT, PrimDef.RIGHT))
        if (Objects.equals(bind.bind().name(), def.ref().name())) {
          subst.bad().add(bind.bind());
          return new Pat.Prim(bind.explicit(), def.ref(), t);
        }
    if (selected == null) {
      exprTycker.localCtx.put(v, t);
      return new Pat.Bind(bind.explicit(), v, t);
    }
    var ctorCore = selected._3.ref().core;
    if (ctorCore.conTele().isNotEmpty()) {
      // TODO: error report: not enough parameters bind
      throw new ExprTycker.TyckerException();
    }
    var value = bind.resolved().value;
    if (value != null) subst.good().putIfAbsent(v, value);
    else subst.bad().add(v);
    return new Pat.Ctor(bind.explicit(), selected._3.ref(), ImmutableSeq.of(), null, selected._1);
  }

  @Override public Pat visitCtor(Pattern.@NotNull Ctor ctor, Term param) {
    var realCtor = selectCtor(param, ctor.name(), subst.reporter(), ctor);
    if (realCtor == null) {
      subst.reporter().report(new UnknownCtorError(ctor));
      throw new ExprTycker.TyckInterruptedException();
    }
    var ctorCore = realCtor._3.ref().core;
    var sig = new Ref<>(new Def.Signature(ImmutableSeq.of(), Term.Param.subst(ctorCore.conTele(), realCtor._2), realCtor._3.underlyingDataCall()));
    var patterns = visitPatterns(sig, ctor.params());
    return new Pat.Ctor(ctor.explicit(), realCtor._3.ref(), patterns, ctor.as(), realCtor._1);
  }

  /**
   * @param name     if null, the selection will be performed on all constructors
   * @param reporter see also {@link IgnoringReporter#INSTANCE}
   * @return null means selection failed
   */
  private @Nullable Tuple3<CallTerm.Data, Substituter.TermSubst, CallTerm.ConHead>
  selectCtor(Term param, @Nullable String name, @NotNull Reporter reporter, @NotNull Pattern pos) {
    if (!(param.normalize(NormalizeMode.WHNF) instanceof CallTerm.Data dataCall)) {
      reporter.report(new SplittingOnNonData(pos, param));
      return null;
    }
    var core = dataCall.ref().core;
    if (core == null) {
      reporter.report(new NotYetTyckedError(pos.sourcePos(), dataCall.ref()));
      return null;
    }
    for (var ctor : core.body()) {
      if (name != null && !Objects.equals(ctor.ref().name(), name)) continue;
      var matchy = mischa(dataCall, core, ctor);
      if (matchy != null) return Tuple.of(dataCall, matchy, dataCall.conHead(ctor.ref()));
      // For absurd pattern, we look at the next constructor
      if (name == null) continue;
      // Since we cannot have two constructors of the same name,
      // if the name-matching constructor mismatches the type,
      // we get an error.
      var severity = reporter == IgnoringReporter.INSTANCE ? Problem.Severity.WARN : Problem.Severity.ERROR;
      subst.reporter().report(new UnavailableCtorError(pos, severity));
      return null;
    }
    return null;
  }

  private @Nullable Substituter.TermSubst mischa(CallTerm.Data dataCall, DataDef core, DataDef.Ctor ctor) {
    if (ctor.pats().isNotEmpty()) return PatMatcher.tryBuildSubstArgs(ctor.pats(), dataCall.args());
    else return Unfolder.buildSubst(core.telescope(), dataCall.args());
  }
}
