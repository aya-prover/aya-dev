// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Result;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.tuple.Tuple3;
import kala.value.MutableValue;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.visitor.PatternTraversal;
import org.aya.core.Matching;
import org.aya.core.def.CtorDef;
import org.aya.core.def.Def;
import org.aya.core.pat.Pat;
import org.aya.core.pat.PatMatcher;
import org.aya.core.repr.AyaShape;
import org.aya.core.term.*;
import org.aya.core.visitor.DeltaExpander;
import org.aya.core.visitor.EndoFunctor;
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
import org.aya.util.TreeBuilder;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author ice1000
 */
public final class PatTycker {
  public static final EndoFunctor META_PAT_INLINER = new EndoFunctor() {
    @Override public @NotNull Term post(@NotNull Term term) {
      return term instanceof RefTerm.MetaPat metaPat ? metaPat.inline() : term;
    }
  };

  public final @NotNull ExprTycker exprTycker;

  /**
   * An {@code as pattern} map.
   * Every binding in the function telescope was treated as an {@code as pattern}
   * so they were added to this map.
   */
  private final @NotNull TypedSubst patSubst;
  private final @Nullable Trace.Builder traceBuilder;
  private boolean hasError = false;
  private Pattern.Clause currentClause = null;

  public boolean noError() {
    return !hasError;
  }

  public PatTycker(
    @NotNull ExprTycker exprTycker,
    @NotNull TypedSubst patSubst,
    @Nullable Trace.Builder traceBuilder
  ) {
    this.exprTycker = exprTycker;
    this.patSubst = patSubst;
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
    this(exprTycker, new TypedSubst(), exprTycker.traceBuilder);
  }

  public record PatResult(
    @NotNull Term result,
    @NotNull ImmutableSeq<Pat.Preclause<Term>> clauses,
    @NotNull ImmutableSeq<Matching> matchings
  ) {
  }

  /**
   * After checking a pattern, we need to replace the references of the
   * corresponding telescope binding with the pattern.
   *
   * TODO[hoshino]: The parameters in the Ctor telescope are also added to patSubst during PatTyck.
   *                It is okay when we tyck a ctor pat, but it becomes useless after tyck:
   *                there is no reference to these variables.
   */
  private void addPatSubst(@NotNull AnyVar var, @NotNull Pat pat, @NotNull Term type) {
    var patTerm = pat.toTerm();
    patSubst.addDirectly(var, patTerm, type);
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
    return clauses.mapIndexed((index, clause) -> traced(
      () -> new Trace.LabelT(clause.sourcePos, "lhs of clause " + (1 + index)),
      () -> checkLhs(clause, signature)));
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

  private @NotNull Pat doTyck(@NotNull Pattern pattern, @NotNull Term term) {
    return switch (pattern) {
      case Pattern.Absurd absurd -> {
        var selection = selectCtor(term, null, absurd);
        if (selection != null) foundError(new PatternProblem.PossiblePat(absurd, selection._3));
        yield new Pat.Absurd(absurd.explicit());
      }
      case Pattern.Tuple tuple -> {
        if (!(term.normalize(exprTycker.state, NormalizeMode.WHNF) instanceof FormTerm.Sigma sigma))
          yield withError(new PatternProblem.TupleNonSig(tuple, term), tuple, term);
        // sig.result is a dummy term
        var sig = new Def.Signature(sigma.params(),
          new ErrorTerm(Doc.plain("Rua"), false));
        var as = tuple.as();
        var ret = new Pat.Tuple(tuple.explicit(), visitPatterns(sig, tuple.patterns().view(), tuple)._1.toImmutableSeq());
        if (as != null) {
          exprTycker.localCtx.put(as, sigma);
          addPatSubst(as, ret, term);
        }
        yield ret;
      }
      case Pattern.Ctor ctor -> {
        var var = ctor.resolved().data();
        var realCtor = selectCtor(term, var, ctor);
        if (realCtor == null) yield randomPat(pattern, term);
        var ctorRef = realCtor._3.ref();
        var ctorCore = ctorRef.core;
        // generate ownerTele arguments
        //
        // The correctness depends on that the `Param#ref`s of `CtorDef#ownerTele`
        // are equal to the `Pat.Bind#bind`s (as they should).
        var ownerTeleArgs = ctorCore.ownerTele.map(x ->
          new RefTerm(x.ref()).subst(realCtor._2));

        final var dataCall = realCtor._1;
        var sig = new Def.Signature(Term.Param.subst(ctorCore.selfTele, realCtor._2, 0), dataCall);
        // It is possible that `ctor.params()` is empty.
        var patterns = visitPatterns(sig, ctor.params().view(), ctor)._1.toImmutableSeq();
        var as = ctor.as();
        var ret = new Pat.Ctor(ctor.explicit(), realCtor._3.ref(), ownerTeleArgs, patterns, dataCall);
        if (as != null) {
          exprTycker.localCtx.put(as, dataCall);
          addPatSubst(as, ret, term);
        }
        yield ret;
      }
      case Pattern.Bind bind -> {
        var v = bind.bind();
        exprTycker.localCtx.put(v, term);
        bind.type().set(term);
        yield new Pat.Bind(bind.explicit(), v, term);
      }
      case Pattern.CalmFace face -> new Pat.Meta(face.explicit(), MutableValue.create(),
        new LocalVar(Constants.ANONYMOUS_PREFIX, face.sourcePos()), term);
      case Pattern.Number num -> {
        var ty = term.normalize(exprTycker.state, NormalizeMode.WHNF);
        if (ty instanceof PrimTerm.Interval) {
          var end = num.number();
          if (end == 0 || end == 1) yield new Pat.End(num.number() == 1, num.explicit());
          yield withError(new PrimError.BadInterval(num.sourcePos(), end), num, term);
        }
        if (ty instanceof CallTerm.Data dataCall) {
          var data = dataCall.ref().core;
          var shape = exprTycker.shapeFactory.find(data);
          if (shape.isDefined() && shape.get() == AyaShape.NAT_SHAPE)
            yield new Pat.ShapedInt(num.number(), shape.get(), dataCall, num.explicit());
        }
        yield withError(new PatternProblem.BadLitPattern(num, term), num, term);
      }
      case Pattern.List list -> {
        // desugar `Pattern.List` to `Pattern.Ctor` here, but use `CodeShape` !
        // Note: this is a special case (maybe), If there is another similar requirement,
        //       a PatternDesugarer is recommended.

        var ty = term.normalize(exprTycker.state, NormalizeMode.WHNF);
        if (ty instanceof CallTerm.Data dataCall) {
          var data = dataCall.ref().core;
          var shape = exprTycker.shapeFactory.find(data);

          if (shape.isDefined() && shape.get() == AyaShape.LIST_SHAPE) {
            yield doTyck(new Pattern.FakeShapedList(
              list.sourcePos(), list.explicit(), list.as(),
              list.elements(), AyaShape.LIST_SHAPE, ty).constructorForm(), term);
          }
        }

        yield withError(new PatternProblem.BadLitPattern(list, term), list, term);
      }
      case Pattern.BinOpSeq binOpSeq -> throw new InternalException("BinOpSeq patterns should be desugared");
    };
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

  private Pat.Preclause<Term> checkRhs(LhsResult lhsResult) {
    var parent = exprTycker.localCtx;
    var parentLets = exprTycker.lets;
    exprTycker.localCtx = lhsResult.gamma;
    // We `addDirectly` to `parentLets`.
    // This means terms in `parentLets` won't be substituted by `lhsResult.bodySubst`
    // TODO[hoshino]: addDirectly or add?
    exprTycker.lets = parentLets.derive().addDirectly(lhsResult.bodySubst());
    var term = lhsResult.preclause.expr().map(e -> lhsResult.hasError
      // In case the patterns are malformed, do not check the body
      // as we bind local variables in the pattern checker,
      // and in case the patterns are malformed, some bindings may
      // not be added to the localCtx of tycker, causing assertion errors
      ? new ErrorTerm(e, false)
      : exprTycker.inherit(e, lhsResult.type).wellTyped());
    exprTycker.localCtx = parent;
    exprTycker.lets = parentLets;
    return new Pat.Preclause<>(lhsResult.preclause.sourcePos(), lhsResult.preclause.patterns(), term);
  }

  private LhsResult checkLhs(Pattern.Clause match, Def.Signature signature) {
    var parent = exprTycker.localCtx;
    exprTycker.localCtx = parent.deriveMap();
    currentClause = match;
    var step0 = visitPatterns(signature, match.patterns.view(), null);

    /// inline
    var patterns = step0._1.map(p -> p.inline(exprTycker.localCtx)).toImmutableSeq();
    var type = META_PAT_INLINER.apply(step0._2);
    patSubst.inline();
    PatternTraversal.visit(p -> {
      if (p instanceof Pattern.Bind bind)
        bind.type().update(t -> t == null ? null : META_PAT_INLINER.apply(t));
    }, match.patterns);

    var step1 = new LhsResult(exprTycker.localCtx, type, patSubst.derive(),
      match.hasError,
      new Pat.Preclause<>(match.sourcePos, patterns, match.expr));
    exprTycker.localCtx = parent;
    patSubst.clear();
    return step1;
  }

  /**
   * Tyck each {@link Pattern} with {@link Def.Signature}.
   * {@param outerPattern} should be specified if stream is empty.
   *
   * @param outerPattern null if visiting the whole pattern (like `A, x, ctor a b`). This is only used for error reporting.
   *                     For now, {@param outerPattern} is used when {@param sig} is not empty
   *                     but {@param stream} is empty, it is possible when matching parameters of Ctor.
   * @return (wellTyped patterns, sig.result ())
   */
  public @NotNull Tuple2<SeqView<Pat>, Term>
  visitPatterns(@NotNull Def.Signature sig, @NotNull SeqView<Pattern> stream, @Nullable Pattern outerPattern) {
    var results = MutableList.<Pat>create();
    if (sig.param().isEmpty() && stream.isEmpty()) return Tuple.of(results.view(), sig.result());
    // last pattern which user given (not aya generated)
    @Nullable Pattern lastPat = null;
    while (sig.param().isNotEmpty()) {
      var param = sig.param().first();
      Pattern pat;
      if (param.explicit()) {
        if (stream.isEmpty()) {
          Pattern errorPattern;

          if (lastPat == null) {
            if (outerPattern == null) {
              throw new InternalException("outerPattern should not be null when stream is empty");
            }

            errorPattern = outerPattern;
          } else {
            errorPattern = lastPat;
          }

          foundError(new PatternProblem.InsufficientPattern(errorPattern, param));
          return done(results, sig.result());
        }
        pat = stream.first();
        lastPat = pat;
        stream = stream.drop(1);
        if (!pat.explicit()) {
          foundError(new PatternProblem.TooManyImplicitPattern(pat, param));
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
      sig = updateSig(new PatData(sig, results, param), pat);
    }
    if (stream.isNotEmpty()) {
      foundError(new PatternProblem
        .TooManyPattern(stream.first(), sig.result().freezeHoles(exprTycker.state)));
    }
    return done(results, sig.result());
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
    public @NotNull SourcePos paramPos() {
      return param.ref().definition();
    }
  }

  private @NotNull PatData beforeMatch(@NotNull PatData data) {
    return new PatData(
      data.sig(), data.results(),
      data.param().subst(patSubst.map())
    );
  }

  private @NotNull PatData afterMatch(@NotNull PatData data) {
    return new PatData(
      new Def.Signature(data.sig().param().drop(1), data.sig().result()),
      data.results(),
      data.param()
    );
  }

  private @NotNull Tuple2<SeqView<Pat>, Term> done(@NotNull SeqLike<Pat> results, @NotNull Term type) {
    return Tuple.of(results.view(), type.subst(patSubst.map()));
  }

  /**
   * A user given pattern matches a parameter, we update the signature.
   */
  private @NotNull Def.Signature updateSig(PatData data, Pattern pat) {
    data = beforeMatch(data);

    var type = data.param.type();
    tracing(builder -> builder.shift(new Trace.PatT(type, pat, pat.sourcePos())));
    var res = doTyck(pat, type);
    tracing(TreeBuilder::reduce);
    addPatSubst(data.param.ref(), res, data.param.type());
    data.results.append(res);

    return afterMatch(data).sig();
  }

  /**
   * For every implicit parameter that not explicitly (no user given pattern) matched,
   * we generate a MetaPat for each,
   * so that they can be inferred during {@link PatTycker#checkLhs(Pattern.Clause, Def.Signature)}
   */
  private @NotNull Def.Signature generatePat(@NotNull PatData data) {
    data = beforeMatch(data);

    var ref = data.param.ref();
    Pat bind;
    var freshVar = new LocalVar(ref.name(), ref.definition());
    if (data.param.type().normalize(exprTycker.state, NormalizeMode.WHNF) instanceof CallTerm.Data dataCall)
      bind = new Pat.Meta(false, MutableValue.create(), freshVar, dataCall);
    else bind = new Pat.Bind(false, freshVar, data.param.type());
    data.results.append(bind);
    exprTycker.localCtx.put(freshVar, data.param.type());
    addPatSubst(ref, bind, data.param.type());

    return afterMatch(data).sig();
  }

  private void foundError(@Nullable Problem problem) {
    hasError = true;
    if (currentClause != null) currentClause.hasError = true;
    if (problem != null) exprTycker.reporter.report(problem);
  }

  private @NotNull Pat withError(Problem problem, Pattern pattern, Term param) {
    foundError(problem);
    // In case something's wrong, produce a random pattern
    return randomPat(pattern, param);
  }

  private @NotNull Pat randomPat(Pattern pattern, Term param) {
    return new Pat.Bind(pattern.explicit(), new LocalVar("?"), param);
  }

  /**
   * @param name if null, the selection will be performed on all constructors
   * @return null means selection failed
   */
  private @Nullable Tuple3<CallTerm.Data, Subst, CallTerm.ConHead>
  selectCtor(Term param, @Nullable AnyVar name, @NotNull Pattern pos) {
    if (!(param.normalize(exprTycker.state, NormalizeMode.WHNF) instanceof CallTerm.Data dataCall)) {
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
      var matchy = mischa(dataCall, ctor, exprTycker.localCtx, exprTycker.state);
      if (matchy.isOk()) return Tuple.of(dataCall, matchy.get(), dataCall.conHead(ctor.ref()));
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

  public static Result<Subst, Boolean>
  mischa(CallTerm.Data dataCall, CtorDef ctor, @Nullable LocalCtx ctx, @NotNull TyckState state) {
    if (ctor.pats.isNotEmpty()) return PatMatcher.tryBuildSubstTerms(ctx, ctor.pats, dataCall.args().view()
      .map(arg -> arg.term().normalize(state, NormalizeMode.WHNF)));
    else return Result.ok(DeltaExpander.buildSubst(Def.defTele(dataCall.ref()), dataCall.args()));
  }
}
