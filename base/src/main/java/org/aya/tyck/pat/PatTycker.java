// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Option;
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
import org.aya.ref.GenerateKind;
import org.aya.ref.LocalVar;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.TyckState;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.error.TyckOrderError;
import org.aya.tyck.trace.Trace;
import org.aya.util.Arg;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

/**
 * @author ice1000
 */
public final class PatTycker {
  public static final EndoTerm META_PAT_INLINER = new EndoTerm() {
    @Override public @NotNull Term post(@NotNull Term term) {
      return term instanceof MetaPatTerm metaPat ? metaPat.inline(this) : term;
    }
  };

  public final @NotNull ExprTycker exprTycker;

  /**
   * An {@code as pattern} map.
   * We are unable to merge this and {@link PatTycker#sigSubst}.
   * Consider this pattern: {@code Ctor (somepat as bind)},
   * the {@code bind} inside the {@link Pat.Ctor} is used in the outside scope (not the {@code Ctor}'s scope).
   */
  private final @NotNull TypedSubst patSubst;

  /**
   * Every binding in the function telescope was treated as an {@code as pattern},
   * but they won't be added to {@link PatTycker#patSubst},
   * because we may visit a completely different signature during tycking {@link Pat.Ctor},
   * and the substs for that signature become useless after tycking {@link Pat.Ctor}
   * (Also, the substs for the function we tycking is useless when we tyck the {@link Pat.Ctor}).
   */
  private final @NotNull TypedSubst sigSubst;
  private boolean hasError = false;

  public PatTycker(
    @NotNull ExprTycker exprTycker,
    @NotNull TypedSubst patSubst,
    @NotNull TypedSubst sigSubst
  ) {
    this.exprTycker = exprTycker;
    this.patSubst = patSubst;
    this.sigSubst = sigSubst;
  }

  public PatTycker(@NotNull ExprTycker exprTycker) {
    this(exprTycker, new TypedSubst(), new TypedSubst());
  }

  public record PatResult(
    @NotNull Term result,
    @NotNull ImmutableSeq<Pat.Preclause<Term>> clauses,
    @NotNull ImmutableSeq<Term.Matching> matchings,
    boolean hasLhsError
  ) {
  }

  public static @NotNull PatResult elabClausesDirectly(
    @NotNull ExprTycker exprTycker,
    @NotNull ImmutableSeq<Pattern.@NotNull Clause> clauses,
    @NotNull Def.Signature<?> signature
  ) {
    return checkAllRhs(exprTycker, checkAllLhs(exprTycker, clauses, signature), signature.result());
  }

  public static @NotNull PatResult elabClausesClassified(
    @NotNull ExprTycker exprTycker,
    @NotNull ImmutableSeq<Pattern.@NotNull Clause> clauses,
    @NotNull Def.Signature<?> signature,
    @NotNull SourcePos overallPos
  ) {
    var lhsResults = checkAllLhs(exprTycker, clauses, signature);
    if (!lhsResults.hasError()) {
      var classes = PatClassifier.classify(lhsResults.lhsResult().view().map(LhsResult::preclause),
        signature.param(), exprTycker, overallPos);
      if (clauses.isNotEmpty()) {
        var usages = PatClassifier.firstMatchDomination(clauses, exprTycker.reporter, classes);
        // refinePatterns(lhsResults, usages, classes);
      }
    }

    return checkAllRhs(exprTycker, lhsResults, signature.result());
  }

  private static @NotNull AllLhsResult checkAllLhs(
    @NotNull ExprTycker exprTycker,
    @NotNull ImmutableSeq<Pattern.@NotNull Clause> clauses,
    @NotNull Def.Signature<?> signature
  ) {
    var inProp = exprTycker.localCtx.with(() ->
      exprTycker.isPropType(signature.result()), signature.param().view());
    return new AllLhsResult(clauses.mapIndexed((index, clause) -> exprTycker.traced(
      () -> new Trace.LabelT(clause.sourcePos, "lhs of clause " + (1 + index)),
      () -> checkLhs(exprTycker, clause, signature, inProp, true))));
  }

  private static @NotNull PatResult checkAllRhs(
    @NotNull ExprTycker exprTycker,
    @NotNull AllLhsResult lhsResult,
    @NotNull Term result
  ) {
    var clauses = lhsResult.lhsResult();
    var res = clauses.mapIndexed((index, lhs) -> exprTycker.traced(
      () -> new Trace.LabelT(lhs.preclause.sourcePos(), "rhs of clause " + (1 + index)),
      () -> checkRhs(exprTycker, lhs)));
    var preclauses = res.map(c -> new Pat.Preclause<>(
      c.sourcePos(), c.patterns().map(p -> p.descent(x -> x.zonk(exprTycker))),
      c.expr().map(exprTycker::zonk)));
    return new PatResult(exprTycker.zonk(result), preclauses,
      preclauses.flatMap(Pat.Preclause::lift), lhsResult.hasError());
  }

  public record AllLhsResult(
    @NotNull ImmutableSeq<LhsResult> lhsResult,
    boolean hasError
  ) {
    public AllLhsResult(@NotNull ImmutableSeq<LhsResult> lhsResults) {
      this(lhsResults, lhsResults.anyMatch(LhsResult::hasError));
    }
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

  /**
   * @param isElim whether this checking is used for elimination (rather than data declaration)
   */
  public static @NotNull LhsResult checkLhs(
    @NotNull ExprTycker exprTycker,
    @NotNull Pattern.Clause match,
    @NotNull Def.Signature<?> signature,
    boolean inProp,
    boolean isElim
  ) {
    var patTycker = new PatTycker(exprTycker);
    return exprTycker.subscoped(() -> {
      // If a pattern occurs in elimination environment, then we check if it contains absurd pattern.
      // If it is not the case, the pattern must be accompanied by a body.
      if (isElim && !match.patterns.anyMatch(p -> patTycker.hasAbsurdity(p.term())) && match.expr.isEmpty()) {
        patTycker.foundError(new PatternProblem.InvalidEmptyBody(match));
      }
      var step0 = patTycker
        .visitPatterns(signature, match.patterns.view(), null, match.expr.getOrNull(), inProp);
      match.hasError = patTycker.hasError;

      var patterns = step0.wellTyped.map(p -> p.descent(x -> x.inline(exprTycker.localCtx))).toImmutableSeq();
      // inline these after inline patterns
      patTycker.patSubst.inline();
      patTycker.sigSubst.inline();
      var type = inlineTerm(step0.codomain);
      exprTycker.localCtx.modifyMyTerms(META_PAT_INLINER);
      var consumer = new PatternConsumer() {
        @Override public void pre(@NotNull Pattern pat) {
          var typeRef = switch (pat) {
            case Pattern.Bind bind -> bind.type();
            case Pattern.As as -> as.type();
            default -> MutableValue.<Term>create();
          };

          typeRef.update(t -> t == null ? null : inlineTerm(t));

          PatternConsumer.super.pre(pat);
        }
      };
      match.patterns.view().map(Arg::term).forEach(consumer::accept);

      return new LhsResult(exprTycker.localCtx, type, patTycker.allSubst(), patTycker.hasError,
        new Pat.Preclause<>(match.sourcePos, patterns, Option.ofNullable(step0.newBody)));
    });
  }

  private static Pat.Preclause<Term> checkRhs(@NotNull ExprTycker exprTycker, @NotNull LhsResult lhsResult) {
    return exprTycker.subscoped(() -> {
      exprTycker.localCtx = lhsResult.gamma;
      var term = exprTycker.withSubSubst(() -> {
        // We `addDirectly` to `parentLets`.
        // This means terms in `parentLets` won't be substituted by `lhsResult.bodySubst`
        exprTycker.lets.addDirectly(lhsResult.bodySubst());
        return lhsResult.preclause.expr().map(e -> lhsResult.hasError
          // In case the patterns are malformed, do not check the body
          // as we bind local variables in the pattern checker,
          // and in case the patterns are malformed, some bindings may
          // not be added to the localCtx of tycker, causing assertion errors
          ? new ErrorTerm(e, false)
          : exprTycker.check(e, lhsResult.type).wellTyped());
      });

      return new Pat.Preclause<>(lhsResult.preclause.sourcePos(), lhsResult.preclause.patterns(), term);
    });
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

  /**
   * check if absurdity is contained in pattern
   */
  private boolean hasAbsurdity(@NotNull Pattern pattern) {
    return switch (pattern) {
      case Pattern.Absurd ignored -> true;
      case Pattern.As as -> hasAbsurdity(as.pattern());
      case Pattern.BinOpSeq binOpSeq -> binOpSeq.seq().anyMatch(p -> hasAbsurdity(p.term()));
      case Pattern.Ctor ctor -> ctor.params().anyMatch(p -> hasAbsurdity(p.term()));
      case Pattern.List list -> list.elements().anyMatch(this::hasAbsurdity);
      case Pattern.Tuple tuple -> tuple.patterns().anyMatch(p -> hasAbsurdity(p.term()));
      default -> false;
    };
  }

  private @NotNull Pat doTyck(@NotNull Pattern pattern, @NotNull Term term, boolean resultIsProp) {
    return switch (pattern) {
      case Pattern.Absurd absurd -> {
        var selection = selectCtor(term, null, absurd);
        if (selection != null) foundError(new PatternProblem.PossiblePat(absurd, selection._3));
        yield new Pat.Absurd();
      }
      case Pattern.Tuple tuple -> {
        if (!(term.normalize(exprTycker.state, NormalizeMode.WHNF) instanceof SigmaTerm sigma))
          yield withError(new PatternProblem.TupleNonSig(tuple, term), term);
        var tupleIsProp = exprTycker.isPropType(sigma);
        if (!resultIsProp && tupleIsProp) foundError(new PatternProblem.IllegalPropPat(tuple));
        // sig.result is a dummy term
        var sig = new Def.Signature<>(sigma.params(),
          new ErrorTerm(Doc.plain("Rua"), false));
        yield new Pat.Tuple(
          visitInnerPatterns(sig, tuple.patterns().view(), tuple, resultIsProp)
            .wellTyped()
            .toImmutableSeq());
      }
      case Pattern.Ctor ctor -> {
        var var = ctor.resolved().data();
        var realCtor = selectCtor(term, var, ctor);
        if (realCtor == null) yield randomPat(term);
        var ctorRef = realCtor._3.ref();
        var dataIsProp = ctorRef.core.inProp();
        if (!resultIsProp && dataIsProp) foundError(new PatternProblem.IllegalPropPat(ctor));
        var ctorCore = ctorRef.core;

        final var dataCall = realCtor._1;
        var sig = new Def.Signature<>(Term.Param.subst(ctorCore.selfTele, realCtor._2, 0), dataCall);
        // It is possible that `ctor.params()` is empty.
        var patterns = visitInnerPatterns(sig, ctor.params().view(), ctor, resultIsProp).wellTyped.toImmutableSeq();
        yield new Pat.Ctor(realCtor._3.ref(), patterns, dataCall);
      }
      case Pattern.Bind(var pos, var bind, var tyExpr, var tyRef) -> {
        exprTycker.localCtx.put(bind, term);
        if (tyExpr != null) exprTycker.withSubSubst(() -> {
          exprTycker.lets.addDirectly(allSubst());
          var syn = exprTycker.synthesize(tyExpr);
          exprTycker.unifyTyReported(term, syn.wellTyped(), tyExpr);
          return null;
        });
        tyRef.set(term);
        yield new Pat.Bind(bind, term);
      }
      case Pattern.CalmFace(var pos) -> new Pat.Meta(MutableValue.create(),
        new LocalVar(Constants.ANONYMOUS_PREFIX, pos, GenerateKind.Anonymous.INSTANCE), term);
      case Pattern.Number(var pos, var number) -> {
        var ty = term.normalize(exprTycker.state, NormalizeMode.WHNF);
        if (ty instanceof DataCall dataCall) {
          var data = dataCall.ref().core;
          var shape = exprTycker.shapeFactory.find(data);
          if (shape.isDefined() && shape.get().shape() == AyaShape.NAT_SHAPE)
            yield new Pat.ShapedInt(number, shape.get(), dataCall);
        }
        yield withError(new PatternProblem.BadLitPattern(pattern, term), term);
      }
      case Pattern.List(var pos, var el) -> {
        // desugar `Pattern.List` to `Pattern.Ctor` here, but use `CodeShape` !
        // Note: this is a special case (maybe), If there is another similar requirement,
        //       a PatternDesugarer is recommended.
        var ty = term.normalize(exprTycker.state, NormalizeMode.WHNF);
        if (ty instanceof DataCall dataCall) {
          var data = dataCall.ref().core;
          var shape = exprTycker.shapeFactory.find(data);
          if (shape.isDefined() && shape.get().shape() == AyaShape.LIST_SHAPE)
            yield doTyck(new Pattern.FakeShapedList(pos, el, shape.get(), dataCall)
              .constructorForm(), term, resultIsProp);
        }
        yield withError(new PatternProblem.BadLitPattern(pattern, term), term);
      }
      case Pattern.As(var pos, var inner, var as, var type) -> {
        var innerPat = doTyck(inner, term, resultIsProp);

        type.set(term);
        addPatSubst(as, innerPat, term);

        yield innerPat;
      }
      case Pattern.QualifiedRef ignored ->  throw new InternalException("QualifiedRef patterns should be desugared");
      case Pattern.BinOpSeq ignored -> throw new InternalException("BinOpSeq patterns should be desugared");
    };
  }

  private record VisitPatterns(
    @NotNull SeqView<Arg<Pat>> wellTyped,
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
   * @see #visitInnerPatterns
   */
  private @NotNull VisitPatterns visitPatterns(
    @NotNull Def.Signature<?> sig,
    @NotNull SeqView<Arg<Pattern>> stream,
    @Nullable Pattern outerPattern,
    @Nullable Expr body,
    boolean resultIsProp
  ) {
    var results = MutableList.<Arg<Pat>>create();
    // last pattern which user given (not aya generated)
    @Nullable Arg<Pattern> lastPat = null;
    while (sig.param().isNotEmpty()) {
      var param = sig.param().first();
      Arg<Pattern> pat;
      // Type explicit, does not have pattern
      if (stream.isEmpty()) {
        if (body instanceof Expr.Lambda(
          var lamPos, var lamParam, var lamBody
        ) && lamParam.explicit() == param.explicit()) {
          body = lamBody;
          var pattern = new Pattern.Bind(lamPos, lamParam.ref(), lamParam.type(), MutableValue.create());
          pat = new Arg<>(pattern, param.explicit());
        } else if (param.explicit()) {
          Pattern errorPattern;

          if (lastPat == null) {
            assert outerPattern != null;
            errorPattern = outerPattern;
          } else {
            errorPattern = lastPat.term();
          }

          foundError(new PatternProblem.InsufficientPattern(errorPattern, param));
          return done(results, sig.result(), body);
        } else {
          // Type is implicit, does not have pattern
          sig = generatePat(new PatData(sig, results, param));
          continue;
        }
      } else if (param.explicit()) {
        // Type explicit, does have pattern
        pat = stream.first();
        lastPat = pat;
        stream = stream.drop(1);
        if (!pat.explicit()) {
          foundError(new PatternProblem.TooManyImplicitPattern(pat.term(), param));
          return done(results, sig.result(), body);
        }
      } else {
        // Type is implicit, does have pattern
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
    return done(results, sig.result(), body);
  }

  private @NotNull VisitPatterns visitInnerPatterns(
    @NotNull Def.Signature<?> sig,
    @NotNull SeqView<Arg<Pattern>> stream,
    @NotNull Pattern outerPattern,
    boolean resultIsProp
  ) {
    var sub = new PatTycker(this.exprTycker, this.patSubst, new TypedSubst());
    var result = sub.visitPatterns(sig, stream, outerPattern, null, resultIsProp);

    this.hasError = hasError || sub.hasError;

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
    @NotNull Def.Signature<?> sig,
    @NotNull MutableList<Arg<Pat>> results,
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
      new Def.Signature<>(data.sig().param().drop(1), data.sig().result()),
      data.results(),
      data.param()
    );
  }

  private @NotNull VisitPatterns done(@NotNull SeqLike<Arg<Pat>> results, @NotNull Term type, @Nullable Expr body) {
    return new VisitPatterns(results.view(), type.subst(sigSubst.map()), body);
  }

  /**
   * A user given pattern matches a parameter, we update the signature.
   *
   * @apiNote {@code data.param.explicit = arg.explicit} or the world explode.
   */
  private @NotNull Def.Signature<?> updateSig(PatData data, Arg<Pattern> arg, boolean resultIsProp) {
    data = beforeTyck(data);

    var type = data.param.type();
    var pat = arg.term();
    var res = exprTycker.traced(() -> new Trace.PatT(type, pat, pat.sourcePos()),
      () -> doTyck(pat, type, resultIsProp));
    addSigSubst(data.param(), res);
    data.results.append(new Arg<>(res, arg.explicit()));

    return afterTyck(data).sig();
  }

  /**
   * For every implicit parameter that not explicitly (no user given pattern) matched,
   * we generate a MetaPat for each,
   * so that they can be inferred during {@link PatTycker#checkLhs(ExprTycker, Pattern.Clause, Def.Signature, boolean, boolean)}
   * @apiNote {@code daat.param.explicit = false} or the world explode.
   */
  private @NotNull Def.Signature<?> generatePat(@NotNull PatData data) {
    data = beforeTyck(data);

    var ref = data.param.ref();
    Pat bind;
    var freshVar = ref.rename();
    if (data.param.type().normalize(exprTycker.state, NormalizeMode.WHNF) instanceof DataCall dataCall) {
      bind = new Pat.Meta(MutableValue.create(), freshVar, dataCall);
    } else {
      bind = new Pat.Bind(freshVar, data.param.type());
      exprTycker.localCtx.put(freshVar, data.param.type());
    }
    data.results.append(new Arg<>(bind, false));
    addSigSubst(data.param(), bind);

    return afterTyck(data).sig();
  }

  private @NotNull Pat randomPat(Term param) {
    return new Pat.Bind(new LocalVar("?"), param);
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
    if (name != null) foundError(new PatternProblem.UnknownCtor(pos));
    return null;
  }

  public static Result<Subst, Boolean> mischa(DataCall dataCall, CtorDef ctor, @NotNull TyckState state) {
    if (ctor.pats.isNotEmpty()) {
      return PatMatcher.tryBuildSubst(true, ctor.pats, dataCall.args(), new Expander.WHNFer(state));
    } else {
      return Result.ok(DeltaExpander.buildSubst(Def.defTele(dataCall.ref()), dataCall.args()));
    }
  }

  /// endregion

  /// region Error Reporting

  private void foundError(@Nullable Problem problem) {
    hasError = true;
    if (problem != null) exprTycker.reporter.report(problem);
  }

  private @NotNull Pat withError(Problem problem, Term param) {
    foundError(problem);
    // In case something's wrong, produce a random pattern
    return randomPat(param);
  }

  /// endregion

  public static @NotNull Term inlineTerm(@NotNull Term term) {
    return META_PAT_INLINER.apply(term);
  }

  public @NotNull TypedSubst allSubst() {
    return patSubst.derive().addDirectly(sigSubst);
  }
}
