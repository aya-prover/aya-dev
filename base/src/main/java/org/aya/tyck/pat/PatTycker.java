// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Result;
import kala.tuple.Tuple;
import kala.tuple.Tuple3;
import kala.value.MutableValue;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.visitor.PatternConsumer;
import org.aya.core.def.CtorDef;
import org.aya.core.def.Def;
import org.aya.core.pat.Pat;
import org.aya.core.pat.PatMatcher;
import org.aya.core.repr.AyaShape;
import org.aya.core.term.*;
import org.aya.core.visitor.DeltaExpander;
import org.aya.core.visitor.EndoTerm;
import org.aya.core.visitor.Expander;
import org.aya.core.visitor.Subst;
import org.aya.generic.Constants;
import org.aya.generic.util.InternalException;
import org.aya.generic.util.NormalizeMode;
import org.aya.pretty.doc.Doc;
import org.aya.ref.AnyVar;
import org.aya.ref.LocalVar;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.TyckState;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.error.PrimError;
import org.aya.tyck.error.TyckOrderError;
import org.aya.tyck.trace.Trace;
import org.aya.util.Arg;
import org.aya.util.TreeBuilder;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author ice1000
 */
public final class PatTycker {
  public static final EndoTerm META_PAT_INLINER = new EndoTerm() {
    @Override public @NotNull Term post(@NotNull Term term) {
      return term instanceof MetaPatTerm metaPat ? metaPat.inline() : term;
    }
  };

  public final @NotNull ExprTycker exprTycker;

  /**
   * An {@code as pattern} map.
   */
  private final @NotNull TypedSubst patSubst;

  /**
   * Every binding in the function telescope was treated as an {@code as pattern},
   * but they won't be added to {@link PatTycker#patSubst},
   * because we may visit a completely different signature during tycking {@link Pat.Ctor},
   * and the substs for that signature become useless after tycking {@link Pat.Ctor}
   * (Also, the substs for the function we tycking is useless when we tyck the {@link Pat.Ctor}).
   */
  private @NotNull TypedSubst sigSubst;
  private final @Nullable Trace.Builder traceBuilder;
  private boolean hasError = false;
  private Pattern.Clause currentClause = null;

  public PatTycker(
    @NotNull ExprTycker exprTycker,
    @NotNull TypedSubst patSubst,
    @NotNull TypedSubst sigSubst,
    @Nullable Trace.Builder traceBuilder
  ) {
    this.exprTycker = exprTycker;
    this.patSubst = patSubst;
    this.sigSubst = sigSubst;
    this.traceBuilder = traceBuilder;
  }

  private <R> R traced(@NotNull Supplier<Trace> trace, @NotNull Supplier<R> computation) {
    tracing(builder -> builder.shift(trace.get()));
    var res = computation.get();
    tracing(TreeBuilder::reduce);
    return res;
  }

  private void tracing(@NotNull Consumer<Trace.@NotNull Builder> consumer) {
    if (traceBuilder != null) consumer.accept(traceBuilder);
  }

  public PatTycker(@NotNull ExprTycker exprTycker) {
    this(exprTycker, new TypedSubst(), new TypedSubst(), exprTycker.traceBuilder);
  }

  public record PatResult(
    @NotNull Term result,
    @NotNull ImmutableSeq<Pat.Preclause<Term>> clauses,
    @NotNull ImmutableSeq<Term.Matching> matchings
  ) {
  }

  public @NotNull PatResult elabClausesDirectly(
    @NotNull ImmutableSeq<Pattern.@NotNull Clause> clauses,
    @NotNull Def.Signature signature
  ) {
    return checkAllRhs(checkAllLhs(clauses, signature), signature.result());
  }

  public @NotNull PatResult elabClausesClassified(
    @NotNull ImmutableSeq<Pattern.@NotNull Clause> clauses,
    @NotNull Def.Signature signature,
    @NotNull SourcePos overallPos
  ) {
    var lhsResults = checkAllLhs(clauses, signature);
    if (noError()) {
      var classes = PatClassifier.classify(lhsResults.view().map(LhsResult::preclause),
        signature.param(), exprTycker, overallPos, true);
      if (clauses.isNotEmpty()) {
        var usages = PatClassifier.firstMatchDomination(clauses, exprTycker.reporter, classes);
        // refinePatterns(lhsResults, usages, classes);
      }
    }
    return checkAllRhs(lhsResults, signature.result());
  }

  private @NotNull ImmutableSeq<LhsResult>
  checkAllLhs(@NotNull ImmutableSeq<Pattern.@NotNull Clause> clauses, @NotNull Def.Signature signature) {
    var inProp = exprTycker.localCtx.with(() ->
      exprTycker.isPropType(signature.result()), signature.param().view());
    return clauses.mapIndexed((index, clause) -> traced(
      () -> new Trace.LabelT(clause.sourcePos, "lhs of clause " + (1 + index)),
      () -> checkLhs(clause, signature, inProp)));
  }

  private @NotNull PatResult checkAllRhs(
    @NotNull ImmutableSeq<LhsResult> clauses,
    @NotNull Term result
  ) {
    var res = clauses.mapIndexed((index, lhs) -> traced(
      () -> new Trace.LabelT(lhs.preclause.sourcePos(), "rhs of clause " + (1 + index)),
      () -> checkRhs(lhs)));
    exprTycker.solveMetas();
    var preclauses = res.map(c -> new Pat.Preclause<>(
      c.sourcePos(), c.patterns().map(p -> p.zonk(exprTycker)),
      c.expr().map(exprTycker::zonk)));
    return new PatResult(exprTycker.zonk(result), preclauses,
      preclauses.flatMap(Pat.Preclause::lift));
  }

  /**
   * @param bodySubst we do need to replace them with the corresponding patterns,
   *                  but patterns are terms (they are already well-typed if not {@param hasError})
   * @param hasError  if there is an error in the patterns
   */
  public record LhsResult(
    @NotNull LocalCtx gamma,
    @NotNull Term type,
    @NotNull TypedSubst bodySubst,
    boolean hasError,
    @NotNull Pat.Preclause<Expr> preclause
  ) {
  }

  public LhsResult checkLhs(Pattern.Clause match, Def.Signature signature, boolean inProp) {
    var parent = exprTycker.localCtx;
    exprTycker.localCtx = parent.deriveMap();
    currentClause = match;
    var step0 = visitPatterns(signature, match.patterns.view(), null, match.expr.getOrNull(), inProp);

    /// inline
    var patterns = step0.wellTyped.map(p -> p.inline(exprTycker.localCtx)).toImmutableSeq();
    var type = inlineTerm(step0.codomain);
    patSubst.inline();
    sigSubst.inline();
    var consumer = new PatternConsumer() {
      @Override public void pre(@NotNull Pattern pat) {
        if (pat instanceof Pattern.Bind bind)
          bind.type().update(t -> t == null ? null : inlineTerm(t));
        PatternConsumer.super.pre(pat);
      }
    };
    match.patterns.view().map(Arg::term).forEach(consumer::accept);

    var subst = patSubst.derive().addDirectly(sigSubst);
    var step1 = new LhsResult(exprTycker.localCtx, type, subst,
      match.hasError,
      new Pat.Preclause<>(match.sourcePos, patterns, match.expr));
    exprTycker.localCtx = parent;
    patSubst.clear();
    sigSubst.clear();
    return step1;
  }

  private Pat.Preclause<Term> checkRhs(LhsResult lhsResult) {
    var parent = exprTycker.localCtx;
    var parentLets = exprTycker.lets;
    exprTycker.localCtx = lhsResult.gamma;
    // We `addDirectly` to `parentLets`.
    // This means terms in `parentLets` won't be substituted by `lhsResult.bodySubst`
    // IDEA said that this line is useless...
    exprTycker.lets = parentLets.derive().addDirectly(lhsResult.bodySubst());
    var term = lhsResult.preclause.expr().map(e -> lhsResult.hasError
      // In case the patterns are malformed, do not check the body
      // as we bind local variables in the pattern checker,
      // and in case the patterns are malformed, some bindings may
      // not be added to the localCtx of tycker, causing assertion errors
      ? new ErrorTerm(e, false)
      : exprTycker.check(e, lhsResult.type).wellTyped());
    exprTycker.localCtx = parent;
    exprTycker.lets = parentLets;
    return new Pat.Preclause<>(lhsResult.preclause.sourcePos(), lhsResult.preclause.patterns(), term);
  }

  /// region Tyck

  /**
   * add an {@code as pattern} subst
   */
  private void addPatSubst(@NotNull AnyVar var, @NotNull Pat pat, @NotNull Term type) {
    patSubst.addDirectly(var, pat.toTerm(), type);
  }

  /**
   * add a {@code parameter} subst
   */
  private void addSigSubst(@NotNull Term.Param param, @NotNull Pat pat) {
    sigSubst.addDirectly(param.ref(), pat.toTerm(), param.type());
  }

  private @NotNull Pat doTyck(@NotNull Pattern pattern, @NotNull Term term, boolean licit, boolean resultIsProp) {
    return switch (pattern) {
      case Pattern.Absurd absurd -> {
        var selection = selectCtor(term, null, absurd);
        if (selection != null) foundError(new PatternProblem.PossiblePat(absurd, selection._3));
        yield new Pat.Absurd(licit);
      }
      case Pattern.Tuple tuple -> {
        if (!(term.normalize(exprTycker.state, NormalizeMode.WHNF) instanceof SigmaTerm sigma))
          yield withError(new PatternProblem.TupleNonSig(tuple, term), licit, term);
        var tupleIsProp = exprTycker.isPropType(sigma);
        if (!resultIsProp && tupleIsProp) foundError(new PatternProblem.IllegalPropPat(tuple));
        // sig.result is a dummy term
        var sig = new Def.Signature(sigma.params(),
          new ErrorTerm(Doc.plain("Rua"), false));
        var as = tuple.as();
        var ret = new Pat.Tuple(licit, visitInnerPatterns(sig, tuple.patterns().view(), tuple, resultIsProp).wellTyped.toImmutableSeq());
        if (as != null) {
          addPatSubst(as, ret, term);
        }
        yield ret;
      }
      case Pattern.Ctor ctor -> {
        var var = ctor.resolved().data();
        var realCtor = selectCtor(term, var, ctor);
        if (realCtor == null) yield randomPat(licit, term);
        var ctorRef = realCtor._3.ref();
        var dataIsProp = ctorRef.core.inProp();
        if (!resultIsProp && dataIsProp) foundError(new PatternProblem.IllegalPropPat(ctor));
        var ctorCore = ctorRef.core;

        final var dataCall = realCtor._1;
        var sig = new Def.Signature(Term.Param.subst(ctorCore.selfTele, realCtor._2, 0), dataCall);
        // It is possible that `ctor.params()` is empty.
        var patterns = visitInnerPatterns(sig, ctor.params().view(), ctor, resultIsProp).wellTyped.toImmutableSeq();
        var as = ctor.as();
        var ret = new Pat.Ctor(licit, realCtor._3.ref(), patterns, dataCall);
        if (as != null) {
          // as pattern === let, so don't add to localCtx
          addPatSubst(as, ret, term);
        }
        yield ret;
      }
      case Pattern.Bind bind -> {
        var v = bind.bind();
        exprTycker.localCtx.put(v, term);
        bind.type().set(term);
        yield new Pat.Bind(licit, v, term);
      }
      case Pattern.CalmFace face -> new Pat.Meta(licit, MutableValue.create(),
        new LocalVar(Constants.ANONYMOUS_PREFIX, face.sourcePos()), term);
      case Pattern.Number num -> {
        var ty = term.normalize(exprTycker.state, NormalizeMode.WHNF);
        if (ty instanceof IntervalTerm) {
          var end = num.number();
          if (end == 0 || end == 1) yield new Pat.End(num.number() == 1, licit);
          yield withError(new PrimError.BadInterval(num.sourcePos(), end), licit, term);
        }
        if (ty instanceof DataCall dataCall) {
          var data = dataCall.ref().core;
          var shape = exprTycker.shapeFactory.find(data);
          if (shape.isDefined() && shape.get().shape() == AyaShape.NAT_SHAPE)
            yield new Pat.ShapedInt(num.number(), shape.get(), dataCall, licit);
        }
        yield withError(new PatternProblem.BadLitPattern(num, term), licit, term);
      }
      case Pattern.List(var pos, var el, var as) -> {
        // desugar `Pattern.List` to `Pattern.Ctor` here, but use `CodeShape` !
        // Note: this is a special case (maybe), If there is another similar requirement,
        //       a PatternDesugarer is recommended.
        var ty = term.normalize(exprTycker.state, NormalizeMode.WHNF);
        if (ty instanceof DataCall dataCall) {
          var data = dataCall.ref().core;
          var shape = exprTycker.shapeFactory.find(data);
          if (shape.isDefined() && shape.get().shape() == AyaShape.LIST_SHAPE)
            yield doTyck(new Pattern.FakeShapedList(pos, as, el, shape.get(), dataCall)
              .constructorForm(), term, licit, resultIsProp);
        }
        yield withError(new PatternProblem.BadLitPattern(pattern, term), licit, term);
      }
      case Pattern.BinOpSeq ignored -> throw new InternalException("BinOpSeq patterns should be desugared");
    };
  }

  private record VisitPatterns(
    @NotNull SeqView<Pat> wellTyped,
    @NotNull Term codomain,
    @UnknownNullability Expr newBody
  ) {
  }

  /**
   * Tyck each {@link Pattern} with {@link Def.Signature}.
   * {@param outerPattern} should be specified if stream is empty.
   *
   * @param outerPattern null if visiting the whole pattern (like `A, x, ctor a b`). This is only used for error reporting.
   *                     For now, {@param outerPattern} is used when {@param sig} is not empty
   *                     but {@param stream} is empty, it is possible when matching parameters of Ctor.
   * @return (wellTyped patterns, sig.result ())
   * @see PatTycker#visitInnerPatterns(Def.Signature, SeqView, Pattern, boolean)
   */
  private @NotNull VisitPatterns visitPatterns(
    @NotNull Def.Signature sig,
    @NotNull SeqView<Arg<Pattern>> stream,
    @Nullable Pattern outerPattern,
    @Nullable Expr body,
    boolean resultIsProp
  ) {
    var results = MutableList.<Pat>create();
    // last pattern which user given (not aya generated)
    @Nullable Arg<Pattern> lastPat = null;
    while (sig.param().isNotEmpty()) {
      var param = sig.param().first();
      Arg<Pattern> pat;
      if (param.explicit()) {
        if (stream.isEmpty()) {
          Pattern errorPattern;

          if (lastPat == null) {
            if (outerPattern == null) {
              throw new InternalException("outerPattern should not be null when stream is empty");
            }

            errorPattern = outerPattern;
          } else {
            errorPattern = lastPat.term();
          }

          foundError(new PatternProblem.InsufficientPattern(errorPattern, param));
          return done(results, sig.result());
        }
        pat = stream.first();
        lastPat = pat;
        stream = stream.drop(1);
        if (!pat.explicit()) {
          foundError(new PatternProblem.TooManyImplicitPattern(pat.term(), param));
          return done(results, sig.result());
        }
      } else {
        // Type is implicit, so....?
        if (stream.isEmpty()) {
          sig = generatePat(new PatData(sig, results, param));
          continue;
        }
        pat = stream.first();
        if (pat.explicit()) {
          // Pattern is explicit, so we leave it to the next type, do not "consume" it
          sig = generatePat(new PatData(sig, results, param));
          continue;
        } else {
          lastPat = pat;
          stream = stream.drop(1);
        }
        // ^ Pattern is implicit, so we "consume" it (stream.drop(1))
      }
      sig = updateSig(new PatData(sig, results, param), pat, resultIsProp);
    }
    if (stream.isNotEmpty()) {
      foundError(new PatternProblem
        .TooManyPattern(stream.first().term(), sig.result().freezeHoles(exprTycker.state)));
    }
    return done(results, sig.result());
  }

  private @NotNull VisitPatterns visitInnerPatterns(
    @NotNull Def.Signature sig,
    @NotNull SeqView<Arg<Pattern>> stream,
    @NotNull Pattern outerPattern,
    boolean resultIsProp
  ) {
    var oldSigSubst = this.sigSubst;
    this.sigSubst = new TypedSubst();
    var result = visitPatterns(sig, stream, outerPattern, null, resultIsProp);

    // recover
    this.sigSubst = oldSigSubst;
    return result;
  }

  /**
   * A data object during PatTyck
   *
   * @param sig     the signature doesn't matched yet (include {@param param})
   * @param results the {@link Pat}s that already tycked
   * @param param   the parameter we want to match
   */
  private record PatData(
    @NotNull Def.Signature sig,
    @NotNull MutableList<Pat> results,
    @NotNull Term.Param param
  ) {
  }

  private @NotNull PatData beforeTyck(@NotNull PatData data) {
    return new PatData(
      data.sig(), data.results(),
      data.param().subst(sigSubst.map())
    );
  }

  private @NotNull PatData afterTyck(@NotNull PatData data) {
    return new PatData(
      new Def.Signature(data.sig().param().drop(1), data.sig().result()),
      data.results(),
      data.param()
    );
  }

  private @NotNull VisitPatterns done(@NotNull SeqLike<Pat> results, @NotNull Term type) {
    return new VisitPatterns(results.view(), type.subst(sigSubst.map()), null);
  }

  /**
   * A user given pattern matches a parameter, we update the signature.
   */
  private @NotNull Def.Signature updateSig(PatData data, Arg<Pattern> arg, boolean resultIsProp) {
    data = beforeTyck(data);

    var type = data.param.type();
    var pat = arg.term();
    tracing(builder -> builder.shift(new Trace.PatT(type, pat, pat.sourcePos())));
    var res = doTyck(pat, type, arg.explicit(), resultIsProp);
    tracing(TreeBuilder::reduce);
    addSigSubst(data.param(), res);
    data.results.append(res);

    return afterTyck(data).sig();
  }

  /**
   * For every implicit parameter that not explicitly (no user given pattern) matched,
   * we generate a MetaPat for each,
   * so that they can be inferred during {@link PatTycker#checkLhs(Pattern.Clause, Def.Signature, boolean)}
   */
  private @NotNull Def.Signature generatePat(@NotNull PatData data) {
    data = beforeTyck(data);

    var ref = data.param.ref();
    Pat bind;
    var freshVar = new LocalVar(ref.name(), ref.definition());
    if (data.param.type().normalize(exprTycker.state, NormalizeMode.WHNF) instanceof DataCall dataCall) {
      bind = new Pat.Meta(false, MutableValue.create(), freshVar, dataCall);
    } else {
      bind = new Pat.Bind(false, freshVar, data.param.type());
      exprTycker.localCtx.put(freshVar, data.param.type());
    }
    data.results.append(bind);
    addSigSubst(data.param(), bind);

    return afterTyck(data).sig();
  }

  private @NotNull Pat randomPat(boolean licit, Term param) {
    return new Pat.Bind(licit, new LocalVar("?"), param);
  }

  /**
   * @param name if null, the selection will be performed on all constructors
   * @return null means selection failed
   */
  private @Nullable Tuple3<DataCall, Subst, ConCall.Head>
  selectCtor(Term param, @Nullable AnyVar name, @NotNull Pattern pos) {
    if (!(param.normalize(exprTycker.state, NormalizeMode.WHNF) instanceof DataCall dataCall)) {
      foundError(new PatternProblem.SplittingOnNonData(pos, param));
      return null;
    }
    var dataRef = dataCall.ref();
    // We are checking an absurd pattern, but the data is not yet fully checked
    var core = dataRef.core;
    if (core == null && name == null) {
      foundError(new TyckOrderError.NotYetTyckedError(pos.sourcePos(), dataRef));
      return null;
    }
    var body = Def.dataBody(dataRef);
    for (var ctor : body) {
      if (name != null && ctor.ref() != name) continue;
      var matchy = mischa(dataCall, ctor, exprTycker.state);
      if (matchy.isOk()) {
        return Tuple.of(dataCall, matchy.get(), dataCall.conHead(ctor.ref()));
      }
      // For absurd pattern, we look at the next constructor
      if (name == null) {
        // Is blocked
        if (matchy.getErr()) {
          foundError(new PatternProblem.BlockedEval(pos, dataCall));
          return null;
        }
        continue;
      }
      // Since we cannot have two constructors of the same name,
      // if the name-matching constructor mismatches the type,
      // we get an error.
      foundError(new PatternProblem.UnavailableCtor(pos, dataCall));
      return null;
    }
    // Here, name != null, and is not in the list of checked body
    if (core == null) {
      foundError(new TyckOrderError.NotYetTyckedError(pos.sourcePos(), name));
      return null;
    }
    return null;
  }

  public static Result<Subst, Boolean> mischa(DataCall dataCall, CtorDef ctor, @NotNull TyckState state) {
    if (ctor.pats.isNotEmpty()) {
      return PatMatcher.tryBuildSubstTerms(true, ctor.pats, dataCall.args().view().map(Arg::term), new Expander.WHNFer(state));
    } else {
      return Result.ok(DeltaExpander.buildSubst(Def.defTele(dataCall.ref()), dataCall.args()));
    }
  }

  /// endregion

  /// region Error Reporting

  private void foundError(@Nullable Problem problem) {
    hasError = true;
    if (currentClause != null) currentClause.hasError = true;
    if (problem != null) exprTycker.reporter.report(problem);
  }

  private @NotNull Pat withError(Problem problem, boolean licit, Term param) {
    foundError(problem);
    // In case something's wrong, produce a random pattern
    return randomPat(licit, param);
  }

  public boolean noError() {
    return !hasError;
  }

  /// endregion

  public static @NotNull Term inlineTerm(@NotNull Term term) {
    return META_PAT_INLINER.apply(term);
  }
}
