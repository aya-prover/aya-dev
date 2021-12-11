// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.collection.mutable.MutableMap;
import kala.control.Result;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.tuple.Tuple3;
import kala.tuple.Unit;
import kala.value.Ref;
import org.aya.api.error.Problem;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.stmt.Decl;
import org.aya.core.TypedMatching;
import org.aya.core.def.CtorDef;
import org.aya.core.def.Def;
import org.aya.core.def.PrimDef;
import org.aya.core.pat.Pat;
import org.aya.core.pat.LhsPatMatcher;
import org.aya.core.term.*;
import org.aya.core.visitor.Substituter;
import org.aya.core.visitor.TermFixpoint;
import org.aya.core.visitor.Unfolder;
import org.aya.generic.Constants;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.TyckState;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.error.NotYetTyckedError;
import org.aya.tyck.trace.Trace;
import org.aya.util.TreeBuilder;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author ice1000
 */
public final class PatTycker {
  private final @NotNull ExprTycker exprTycker;
  private final @NotNull Substituter.TermSubst termSubst;
  private final @Nullable Trace.Builder traceBuilder;
  private boolean hasError = false;
  private Pattern.Clause currentClause = null;

  public boolean noError() {
    return !hasError;
  }

  public PatTycker(
    @NotNull ExprTycker exprTycker,
    @NotNull Substituter.TermSubst termSubst,
    @Nullable Trace.Builder traceBuilder
  ) {
    this.exprTycker = exprTycker;
    this.termSubst = termSubst;
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
    this(exprTycker, new Substituter.TermSubst(MutableMap.create()), exprTycker.traceBuilder);
  }

  public record PatResult(
    @NotNull Term result,
    @NotNull ImmutableSeq<Pat.Preclause<Term>> clauses,
    @NotNull ImmutableSeq<TypedMatching> matchings
  ) {
  }

  public @NotNull PatResult elabClausesDirectly(
    @NotNull ImmutableSeq<Pattern.@NotNull Clause> clauses,
    @NotNull Def.Signature signature, @NotNull SourcePos resultPos
  ) {
    return checkAllRhs(checkAllLhs(clauses, signature), resultPos, signature.result());
  }

  public @NotNull PatResult elabClausesClassified(
    @NotNull ImmutableSeq<Pattern.@NotNull Clause> clauses,
    @NotNull Def.Signature signature,
    @NotNull SourcePos resultPos, @NotNull SourcePos overallPos
  ) {
    var lhsResults = checkAllLhs(clauses, signature);
    if (noError()) {
      var classes = PatClassifier.classify(lhsResults.view().map(LhsResult::preclause),
        signature.param(), exprTycker, overallPos, true);
      var usages = PatClassifier.firstMatchDomination(clauses, exprTycker.reporter, classes);
      // refinePatterns(lhsResults, usages, classes);
    }
    return checkAllRhs(lhsResults, resultPos, signature.result());
  }

  private @NotNull ImmutableSeq<LhsResult>
  checkAllLhs(@NotNull ImmutableSeq<Pattern.@NotNull Clause> clauses, @NotNull Def.Signature signature) {
    return clauses.mapIndexed((index, clause) -> traced(
      () -> new Trace.LabelT(clause.sourcePos, "lhs of clause " + (1 + index)),
      () -> checkLhs(clause, signature)));
  }

  private @NotNull PatResult checkAllRhs(
    @NotNull ImmutableSeq<LhsResult> clauses,
    @NotNull SourcePos resultPos, @NotNull Term result
  ) {
    var res = clauses.mapIndexed((index, lhs) -> traced(
      () -> new Trace.LabelT(lhs.preclause.sourcePos(), "rhs of clause " + (1 + index)),
      () -> checkRhs(lhs)));
    exprTycker.solveMetas();
    var zonker = exprTycker.newZonker();
    var preclauses = res.map(c -> new Pat.Preclause<>(
      c.sourcePos(), c.patterns().map(p -> p.zonk(zonker)),
      c.expr().map(e -> zonker.zonk(e, c.sourcePos()))));
    return new PatResult(zonker.zonk(result, resultPos), preclauses,
      preclauses.flatMap(Pat.Preclause::lift));
  }

  @SuppressWarnings("unchecked") private @NotNull Pat doTyck(@NotNull Pattern pattern, @NotNull Term term) {
    return switch (pattern) {
      case Pattern.Absurd absurd -> {
        var selection = selectCtor(term, null, absurd);
        if (selection != null) {
          foundError(new PatternProblem.PossiblePat(absurd, selection._3));
        }
        yield new Pat.Absurd(absurd.explicit(), term);
      }
      case Pattern.Tuple tuple -> {
        if (!(term.normalize(exprTycker.state, NormalizeMode.WHNF) instanceof FormTerm.Sigma sigma))
          yield withError(new PatternProblem.TupleNonSig(tuple, term), tuple, term);
        // sig.result is a dummy term
        var sig = new Def.Signature(
          ImmutableSeq.empty(),
          sigma.params(),
          new ErrorTerm(Doc.plain("Rua"), false));
        var as = tuple.as();
        var ret = new Pat.Tuple(tuple.explicit(),
          visitPatterns(sig, tuple.patterns().view())._1, sigma);
        if (as != null) {
          exprTycker.localCtx.put(as, sigma);
          termSubst.addDirectly(as, ret.toTerm());
        }
        yield ret;
      }
      case Pattern.Ctor ctor -> {
        var var = ctor.resolved().data();
        if (term instanceof CallTerm.Prim prim
          && prim.ref().core.id == PrimDef.ID.INTERVAL
          && var instanceof DefVar<?, ?> defVar
          && defVar.core instanceof PrimDef def
          && PrimDef.Factory.LEFT_RIGHT.contains(def.id)
        ) yield new Pat.Prim(ctor.explicit(), (DefVar<PrimDef, Decl.PrimDecl>) defVar, term);
        var realCtor = selectCtor(term, var, ctor);
        if (realCtor == null) yield randomPat(pattern, term);
        var ctorRef = realCtor._3.ref();
        var ctorCore = ctorRef.core;
        final var dataCall = realCtor._1;
        var levelSubst = Unfolder.buildSubst(Def.defLevels(dataCall.ref()), dataCall.sortArgs());
        var sig = new Def.Signature(ImmutableSeq.empty(),
          Term.Param.subst(ctorCore.selfTele, realCtor._2, levelSubst), dataCall);
        var patterns = visitPatterns(sig, ctor.params().view())._1;
        var as = ctor.as();
        var ret = new Pat.Ctor(ctor.explicit(), realCtor._3.ref(), patterns, dataCall);
        if (as != null) {
          exprTycker.localCtx.put(as, dataCall);
          termSubst.addDirectly(as, ret.toTerm());
        }
        yield ret;
      }
      case Pattern.Bind bind -> {
        var v = bind.bind();
        exprTycker.localCtx.put(v, term);
        yield new Pat.Bind(bind.explicit(), v, term);
      }
      case Pattern.CalmFace face -> new Pat.Meta(face.explicit(), new Ref<>(),
        new LocalVar(Constants.ANONYMOUS_PREFIX, face.sourcePos()), term);
      case default -> throw new UnsupportedOperationException("Number patterns are unsupported yet");
    };
  }

  public record LhsResult(
    @NotNull LocalCtx gamma,
    @NotNull Term type,
    @NotNull Substituter.TermSubst subst,
    boolean hasError,
    @NotNull Pat.Preclause<Expr> preclause
  ) {
  }

  private Pat.Preclause<Term> checkRhs(LhsResult lhsResult) {
    var parent = exprTycker.localCtx;
    exprTycker.localCtx = lhsResult.gamma;
    var patterns = lhsResult.preclause.patterns().map(Pat::inline);
    var type = lhsResult.type.accept(new TermFixpoint<>() {
      @Override public @NotNull Term visitMetaPat(@NotNull RefTerm.MetaPat metaPat, Unit unit) {
        return metaPat.inline();
      }
    }, Unit.unit());
    var term = lhsResult.preclause.expr().map(e -> lhsResult.hasError
      // In case the patterns are malformed, do not check the body
      // as we bind local variables in the pattern checker,
      // and in case the patterns are malformed, some bindings may
      // not be added to the localCtx of tycker, causing assertion errors
      ? new ErrorTerm(e, false)
      : exprTycker.inherit(e, type).wellTyped().subst(lhsResult.subst));
    exprTycker.localCtx = parent;
    return new Pat.Preclause<>(lhsResult.preclause.sourcePos(), patterns, term);
  }

  private LhsResult checkLhs(Pattern.Clause match, Def.Signature signature) {
    var parent = exprTycker.localCtx;
    exprTycker.localCtx = parent.deriveMap();
    currentClause = match;
    var patResult = visitPatterns(signature, match.patterns.view());
    var subst = termSubst.replicate();
    termSubst.clear();
    var gamma = exprTycker.localCtx;
    exprTycker.localCtx = parent;
    return new LhsResult(gamma, patResult._2, subst, match.hasError,
      new Pat.Preclause<>(match.sourcePos, patResult._1, match.expr));
  }

  public @NotNull Tuple2<ImmutableSeq<Pat>, Term>
  visitPatterns(Def.Signature sig, SeqView<Pattern> stream) {
    var results = DynamicSeq.<Pat>create();
    if (sig.param().isEmpty() && stream.isEmpty()) return Tuple.of(results.toImmutableSeq(), sig.result());
    Pattern last_pat = stream.last();
    while (sig.param().isNotEmpty()) {
      var param = sig.param().first();
      Pattern pat;
      if (param.explicit()) {
        if (stream.isEmpty()) {
          foundError(new PatternProblem.InsufficientPattern(last_pat, param));
          return Tuple.of(results.toImmutableSeq(), sig.result());
        }
        pat = stream.first();
        stream = stream.drop(1);
        if (!pat.explicit()) {
          foundError(new PatternProblem.TooManyImplicitPattern(pat, param));
          return Tuple.of(results.toImmutableSeq(), sig.result());
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
        } else stream = stream.drop(1);
        // ^ Pattern is implicit, so we "consume" it (stream.drop(1))
      }
      sig = updateSig(new PatData(sig, results, param), pat);
    }
    if (stream.isNotEmpty()) {
      foundError(new PatternProblem
        .TooManyPattern(stream.first(), sig.result().freezeHoles(exprTycker.state)));
    }
    return Tuple.of(results.toImmutableSeq(), sig.result());
  }

  private record PatData(Def.Signature sig, DynamicSeq<Pat> results, Term.Param param) {
  }

  private @NotNull Def.Signature updateSig(PatData data, Pattern pat) {
    var type = data.param.type();
    tracing(builder -> builder.shift(new Trace.PatT(type, pat, pat.sourcePos())));
    var res = doTyck(pat, type);
    tracing(TreeBuilder::reduce);
    termSubst.add(data.param.ref(), res.toTerm());
    data.results.append(res);
    return data.sig.inst(termSubst);
  }

  private @NotNull Def.Signature generatePat(PatData data) {
    var ref = data.param.ref();
    Pat bind;
    var freshVar = new LocalVar(ref.name(), ref.definition());
    if (data.param.type().normalize(exprTycker.state, NormalizeMode.WHNF) instanceof CallTerm.Data dataCall)
      bind = new Pat.Meta(false, new Ref<>(), freshVar, data.param.type());
    else bind = new Pat.Bind(false, freshVar, data.param.type());
    data.results.append(bind);
    exprTycker.localCtx.put(freshVar, data.param.type());
    termSubst.add(ref, bind.toTerm());
    return data.sig.inst(termSubst);
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
  private @Nullable Tuple3<CallTerm.Data, Substituter.TermSubst, CallTerm.ConHead>
  selectCtor(Term param, @Nullable Var name, @NotNull Pattern pos) {
    if (!(param.normalize(exprTycker.state, NormalizeMode.WHNF) instanceof CallTerm.Data dataCall)) {
      foundError(new PatternProblem.SplittingOnNonData(pos, param));
      return null;
    }
    var dataRef = dataCall.ref();
    // We are checking an absurd pattern, but the data is not yet fully checked
    var core = dataRef.core;
    if (core == null && name == null) {
      foundError(new NotYetTyckedError(pos.sourcePos(), dataRef));
      return null;
    }
    var body = Def.dataBody(dataRef);
    var telescope = Def.defTele(dataRef);
    for (var ctor : body) {
      if (name != null && ctor.ref() != name) continue;
      var matchy = mischa(dataCall, ctor, telescope);
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
      foundError(new NotYetTyckedError(pos.sourcePos(), name));
      return null;
    }
    return null;
  }

  private Result<Substituter.TermSubst, Boolean> mischa(CallTerm.Data dataCall, CtorDef ctor, @NotNull ImmutableSeq<Term.Param> telescope) {
    if (ctor.pats.isNotEmpty()) return dataArgs(dataCall, ctor, exprTycker.localCtx, exprTycker.state);
    else return Result.ok(Unfolder.buildSubst(telescope, dataCall.args()));
  }

  public static Result<Substituter.TermSubst, Boolean>
  dataArgs(@NotNull CallTerm.Data dataCall, @NotNull CtorDef ctor, @Nullable LocalCtx ctx, @NotNull TyckState state) {
    return LhsPatMatcher.tryBuildPatSubstTerms(ctx, ctor.pats, dataCall.args().view()
      .map(arg -> arg.term().normalize(state, NormalizeMode.WHNF)));
  }
}
