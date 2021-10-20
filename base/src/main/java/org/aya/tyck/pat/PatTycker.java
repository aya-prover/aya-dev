// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.tuple.Tuple3;
import kala.tuple.Unit;
import kala.value.Ref;
import org.aya.api.error.IgnoringReporter;
import org.aya.api.error.Problem;
import org.aya.api.error.Reporter;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.Pattern;
import org.aya.concrete.visitor.ExprRefSubst;
import org.aya.core.def.CtorDef;
import org.aya.core.def.DataDef;
import org.aya.core.def.Def;
import org.aya.core.def.PrimDef;
import org.aya.core.pat.Pat;
import org.aya.core.pat.PatMatcher;
import org.aya.core.term.CallTerm;
import org.aya.core.term.ErrorTerm;
import org.aya.core.term.FormTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.Substituter;
import org.aya.core.visitor.Unfolder;
import org.aya.generic.GenericBuilder;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.error.NotYetTyckedError;
import org.aya.tyck.trace.Trace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * @author ice1000
 */
public final class PatTycker {
  private final @NotNull ExprTycker exprTycker;
  public final @NotNull ExprRefSubst refSubst;
  private final Substituter.@NotNull TermSubst termSubst;
  private final Trace.@Nullable Builder traceBuilder;
  private boolean hasError = false;
  private Pattern.Clause currentClause = null;

  public boolean noError() {
    return !hasError;
  }

  public PatTycker(
    @NotNull ExprTycker exprTycker,
    @NotNull ExprRefSubst refSubst,
    @NotNull Substituter.TermSubst termSubst,
    @Nullable Trace.Builder traceBuilder
  ) {
    this.exprTycker = exprTycker;
    this.refSubst = refSubst;
    this.termSubst = termSubst;
    this.traceBuilder = traceBuilder;
  }

  private void tracing(@NotNull Consumer<Trace.@NotNull Builder> consumer) {
    if (traceBuilder != null) consumer.accept(traceBuilder);
  }

  public PatTycker(@NotNull ExprTycker exprTycker) {
    this(exprTycker, new ExprRefSubst(exprTycker.reporter),
      new Substituter.TermSubst(MutableMap.create()), exprTycker.traceBuilder);
  }

  public @NotNull Tuple2<@NotNull Term, @NotNull ImmutableSeq<Pat.PrototypeClause>>
  elabClauses(
    @NotNull ImmutableSeq<Pattern.@NotNull Clause> clauses,
    @NotNull Def.Signature signature
  ) {
    var res = clauses.mapIndexed((index, clause) -> {
      tracing(builder -> builder.shift(new Trace.LabelT(clause.sourcePos, "clause " + (1 + index))));
      refSubst.clear();
      var elabClause = visitMatch(clause, signature);
      tracing(GenericBuilder::reduce);
      return elabClause;
    });
    exprTycker.solveMetas();
    return Tuple.of(signature.result().zonk(exprTycker, null), res.map(c -> c.mapTerm(e -> e.zonk(exprTycker, null))));
  }

  @NotNull public ImmutableSeq<Pat.PrototypeClause> elabClauses(
    @Nullable ExprRefSubst patSubst, Def.Signature signature,
    @NotNull ImmutableSeq<Pattern.Clause> clauses
  ) {
    var checked = clauses.map(c -> {
      if (patSubst != null) refSubst.resetTo(patSubst);
      return Tuple.of(visitMatch(c, signature), c.sourcePos);
    });
    exprTycker.solveMetas();
    return checked.map(c -> c._1.mapTerm(e -> e.zonk(exprTycker, c._2)));
  }

  private @NotNull Pat doTyck(@NotNull Pattern pattern, @NotNull Term term) {
    return switch (pattern) {
      case Pattern.Absurd absurd -> {
        var selection = selectCtor(term, null, refSubst.reporter(), absurd);
        if (selection != null) {
          foundError();
          refSubst.reporter().report(new PatternProblem.PossiblePat(absurd, selection._3));
        }
        yield new Pat.Absurd(absurd.explicit(), term);
      }
      case Pattern.Tuple tuple -> {
        if (!(term instanceof FormTerm.Sigma sigma))
          yield withError(new PatternProblem.TupleNonSig(tuple, term), tuple, "?", term);
        // sig.result is a dummy term
        var sig = new Def.Signature(
          ImmutableSeq.empty(),
          sigma.params(),
          new ErrorTerm(Doc.plain("Rua"), false));
        var as = tuple.as();
        if (as != null) exprTycker.localCtx.put(as, sigma);
        yield new Pat.Tuple(tuple.explicit(),
          visitPatterns(new Ref<>(sig), tuple.patterns()), as, sigma);
      }
      case Pattern.Ctor ctor -> {
        var realCtor = selectCtor(term, ctor.name().data(), refSubst.reporter(), ctor);
        if (realCtor == null) yield withError(new PatternProblem.UnknownCtor(ctor), ctor, ctor.name().data(), term);
        var ctorRef = realCtor._3.ref();
        ctor.resolved().value = ctorRef;
        var ctorCore = ctorRef.core;
        final var dataCall = realCtor._1;
        var levelSubst = Unfolder.buildSubst(Def.defLevels(dataCall.ref()), dataCall.sortArgs());
        var sig = new Ref<>(new Def.Signature(ImmutableSeq.empty(),
          Term.Param.subst(ctorCore.selfTele, realCtor._2, levelSubst), dataCall));
        var patterns = visitPatterns(sig, ctor.params());
        yield new Pat.Ctor(ctor.explicit(), realCtor._3.ref(), patterns, ctor.as(), realCtor._1);
      }
      case Pattern.Bind bind -> {
        var v = bind.bind();
        var interval = PrimDef.Factory.INSTANCE.getOption(PrimDef.ID.INTERVAL);
        if (term instanceof CallTerm.Prim prim && interval.isNotEmpty() && prim.ref() == interval.get().ref())
          for (var primName : PrimDef.Factory.LEFT_RIGHT)
            if (Objects.equals(bind.bind().name(), primName.id)) {
              refSubst.bad().add(bind.bind());
              yield new Pat.Prim(bind.explicit(), PrimDef.Factory.INSTANCE.getOption(primName).get().ref(), term);
            }
        var selected = selectCtor(term, v.name(), IgnoringReporter.INSTANCE, bind);
        if (selected == null) {
          exprTycker.localCtx.put(v, term);
          yield new Pat.Bind(bind.explicit(), v, term);
        }
        var ctorCore = selected._3.ref().core;
        if (ctorCore.selfTele.isNotEmpty()) {
          // TODO: error report: not enough parameters bind
          foundError();
          throw new ExprTycker.TyckerException();
        }
        var value = bind.resolved().value;
        if (value != null) refSubst.good().putIfAbsent(v, value);
        else refSubst.bad().add(v);
        yield new Pat.Ctor(bind.explicit(), selected._3.ref(), ImmutableSeq.empty(), null, selected._1);
      }
      case default -> throw new UnsupportedOperationException("Number and underscore patterns are unsupported yet");
    };
  }

  private Pat.PrototypeClause visitMatch(Pattern.@NotNull Clause match, Def.@NotNull Signature signature) {
    var sig = new Ref<>(signature);
    exprTycker.localCtx = exprTycker.localCtx.derive();
    currentClause = match;
    var patterns = visitPatterns(sig, match.patterns);
    var type = sig.value.result();
    match.expr = match.expr.map(e -> e.accept(refSubst, Unit.unit()));
    var result = match.hasError
      // In case the patterns are malformed, do not check the body
      // as we bind local variables in the pattern checker,
      // and in case the patterns are malformed, some bindings may
      // not be added to the localCtx of tycker, causing assertion errors
      ? match.expr.<Term>map(e -> new ErrorTerm(e, false))
      : match.expr.map(e -> exprTycker.inherit(e, type).wellTyped().subst(termSubst));
    termSubst.clear();
    var parent = exprTycker.localCtx.parent();
    assert parent != null;
    exprTycker.localCtx = parent;
    return new Pat.PrototypeClause(match.sourcePos, patterns, result);
  }

  public @NotNull ImmutableSeq<Pat> visitPatterns(Ref<Def.Signature> sig, ImmutableSeq<Pattern> stream) {
    var results = Buffer.<Pat>create();
    stream.forEach(pat -> {
      if (sig.value.param().isEmpty()) {
        exprTycker.reporter.report(new PatternProblem.TooManyPattern(pat, sig.value.result()));
        foundError();
        return;
      }
      var param = sig.value.param().first();
      while (param.explicit() != pat.explicit()) if (pat.explicit()) {
        // TODO: implicitly generated patterns might be inferred to something else?
        var bind = new Pat.Bind(false, new LocalVar(param.ref().name(), param.ref().definition()), param.type());
        results.append(bind);
        exprTycker.localCtx.put(bind.as(), param.type());
        termSubst.add(param.ref(), bind.toTerm());
        sig.value = sig.value.inst(termSubst);
        if (sig.value.param().isEmpty()) {
          // TODO[ice]: report error
          foundError();
          throw new ExprTycker.TyckerException();
        }
        param = sig.value.param().first();
      } else {
        // TODO[ice]: unexpected implicit pattern
        foundError();
        throw new ExprTycker.TyckerException();
      }
      var type = param.type();
      tracing(builder -> builder.shift(new Trace.PatT(type, pat, pat.sourcePos())));
      var res = doTyck(pat, type);
      tracing(GenericBuilder::reduce);
      termSubst.add(param.ref(), res.toTerm());
      sig.value = sig.value.inst(termSubst);
      results.append(res);
    });
    return results.toImmutableSeq();
  }

  private void foundError() {
    hasError = true;
    if (currentClause != null) currentClause.hasError = true;
  }

  private @NotNull Pat withError(Problem problem, Pattern pattern, String name, Term param) {
    exprTycker.reporter.report(problem);
    foundError();
    // In case something's wrong, produce a random pattern
    return new Pat.Bind(pattern.explicit(), new LocalVar(name), param);
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
    for (var ctor : core.body) {
      if (name != null && !Objects.equals(ctor.ref().name(), name)) continue;
      var matchy = mischa(dataCall, core, ctor);
      if (matchy != null) return Tuple.of(dataCall, matchy, dataCall.conHead(ctor.ref()));
      // For absurd pattern, we look at the next constructor
      if (name == null) continue;
      // Since we cannot have two constructors of the same name,
      // if the name-matching constructor mismatches the type,
      // we get an error.
      var severity = reporter == IgnoringReporter.INSTANCE ? Problem.Severity.WARN : Problem.Severity.ERROR;
      refSubst.reporter().report(new PatternProblem.UnavailableCtor(pos, severity));
      return null;
    }
    return null;
  }

  private @Nullable Substituter.TermSubst mischa(CallTerm.Data dataCall, DataDef core, CtorDef ctor) {
    if (ctor.pats.isNotEmpty()) return PatMatcher.tryBuildSubstArgs(ctor.pats, dataCall.args());
    else return Unfolder.buildSubst(core.telescope(), dataCall.args());
  }
}
