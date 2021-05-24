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
import org.aya.core.term.FormTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.Substituter;
import org.aya.core.visitor.Unfolder;
import org.aya.generic.GenericBuilder;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.error.NotYetTyckedError;
import org.aya.tyck.error.PatternProblem;
import org.aya.tyck.trace.Trace;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
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

  @Override public void traceEntrance(@NotNull Pattern pat, Term term) {
    tracing(builder -> builder.shift(new Trace.PatT(term, pat, pat.sourcePos())));
  }

  @Override public void traceExit(Pat pat, @NotNull Pattern pattern, Term term) {
    tracing(builder -> {
      builder.reduce();
      var pos = pattern instanceof Pattern.Ctor c ? c.name().sourcePos() : pattern.sourcePos();
      builder.collect(pat, pos);
    });
  }

  public PatTycker(@NotNull ExprTycker exprTycker) {
    this(exprTycker, new ExprRefSubst(exprTycker.reporter), exprTycker.traceBuilder);
  }

  public @NotNull Tuple2<@NotNull Term, @NotNull ImmutableSeq<Pat.PrototypeClause>> elabClauses(
    @NotNull ImmutableSeq<Pattern.@NotNull Clause> clauses,
    Ref<Def.@NotNull Signature> signature
  ) {
    var res = clauses.mapIndexed((index, clause) -> {
      tracing(builder -> builder.shift(new Trace.LabelT(clause.sourcePos(), "clause " + (1 + index))));
      subst.clear();
      var elabClause = visitMatch(clause, signature.value);
      tracing(GenericBuilder::reduce);
      return elabClause;
    });
    exprTycker.equations.solve();
    return Tuple.of(signature.value.result().zonk(exprTycker), res.map(c -> c.mapTerm(e -> e.zonk(exprTycker))));
  }

  @NotNull public ImmutableSeq<Pat.PrototypeClause> elabClauses(
    @Nullable ExprRefSubst patSubst, Def.Signature signature,
    @NotNull ImmutableSeq<Pattern.Clause> clauses
  ) {
    var checked = clauses.map(c -> {
      if (patSubst != null) subst().resetTo(patSubst);
      return visitMatch(c, signature);
    });
    exprTycker.equations.solve();
    return checked.map(c -> c.mapTerm(e -> e.zonk(exprTycker)));
  }

  @Override public Pat visitAbsurd(Pattern.@NotNull Absurd absurd, Term term) {
    var selection = selectCtor(term, null, subst.reporter(), absurd);
    if (selection != null) {
      subst.reporter().report(new PatternProblem.PossiblePat(absurd, selection._3));
      // This is actually not necessary. Do we want to delete it?
      throw new ExprTycker.TyckInterruptedException();
    }
    return new Pat.Absurd(absurd.explicit(), term);
  }

  public Pat.PrototypeClause visitMatch(Pattern.@NotNull Clause match, Def.@NotNull Signature signature) {
    var sig = new Ref<>(signature);
    exprTycker.localCtx = exprTycker.localCtx.derive();
    var patterns = visitPatterns(sig, match.patterns());
    var type = sig.value.result();
    var result = match.expr()
      .map(e -> e.accept(subst, Unit.unit()))
      .map(e -> exprTycker.checkNoZonk(e, type));
    var parent = exprTycker.localCtx.parent();
    assert parent != null;
    exprTycker.localCtx = parent;
    return new Pat.PrototypeClause(match.sourcePos(), patterns, result.map(ExprTycker.Result::wellTyped));
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
        exprTycker.localCtx.put(bind.as(), param.type());
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
    if (tuple.as() != null) exprTycker.localCtx.put(tuple.as(), t);
    if (!(t instanceof FormTerm.Sigma sigma)) {
      // TODO[ice]: requires pretty printing patterns
      throw new ExprTycker.TyckerException();
    }
    // sig.result is a dummy term
    var sig = new Def.Signature(
      ImmutableSeq.of(),
      sigma.params(),
      FormTerm.Univ.OMEGA);
    if (tuple.as() != null) exprTycker.localCtx.put(tuple.as(), sigma);
    return new Pat.Tuple(tuple.explicit(),
      visitPatterns(new Ref<>(sig), tuple.patterns()), tuple.as(), sigma);
  }

  @Override public Pat visitBind(Pattern.@NotNull Bind bind, Term t) {
    var v = bind.bind();
    var selected = selectCtor(t, v.name(), IgnoringReporter.INSTANCE, bind);
    if (t instanceof CallTerm.Prim prim && prim.ref().core == PrimDef.INTERVAL)
      for (var def : PrimDef.LEFT_RIGHT)
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
    var realCtor = selectCtor(param, ctor.name().data(), subst.reporter(), ctor);
    if (realCtor == null) {
      subst.reporter().report(new PatternProblem.UnknownCtor(ctor));
      throw new ExprTycker.TyckInterruptedException();
    }
    var ctorCore = realCtor._3.ref().core;
    final var dataCall = realCtor._1;
    var sig = new Ref<>(new Def.Signature(ImmutableSeq.of(),
      Term.Param.subst(ctorCore.conTele(), realCtor._2,
        Unfolder.buildSubst(Def.defLevels(dataCall.ref()), dataCall.sortArgs())), dataCall));
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
      reporter.report(new PatternProblem.SplittingOnNonData(pos, param));
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
      subst.reporter().report(new PatternProblem.UnavailableCtor(pos, severity));
      return null;
    }
    return null;
  }

  private @Nullable Substituter.TermSubst mischa(CallTerm.Data dataCall, DataDef core, DataDef.Ctor ctor) {
    if (ctor.pats().isNotEmpty()) return PatMatcher.tryBuildSubstArgs(ctor.pats(), dataCall.args());
    else return Unfolder.buildSubst(core.telescope(), dataCall.args());
  }
}
