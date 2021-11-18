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
import org.aya.api.error.Problem;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.Pattern;
import org.aya.concrete.stmt.Decl;
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
import org.aya.pretty.doc.Doc;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.error.NotYetTyckedError;
import org.aya.tyck.trace.Trace;
import org.aya.util.TreeBuilder;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * @author ice1000
 */
public final class PatTycker {
  private final @NotNull ExprTycker exprTycker;
  private final Substituter.@NotNull TermSubst termSubst;
  private final Trace.@Nullable Builder traceBuilder;
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

  private void tracing(@NotNull Consumer<Trace.@NotNull Builder> consumer) {
    if (traceBuilder != null) consumer.accept(traceBuilder);
  }

  public PatTycker(@NotNull ExprTycker exprTycker) {
    this(exprTycker, new Substituter.TermSubst(MutableMap.create()), exprTycker.traceBuilder);
  }

  public @NotNull Tuple2<@NotNull Term, @NotNull ImmutableSeq<Pat.PrototypeClause>>
  elabClauses(
    @NotNull ImmutableSeq<Pattern.@NotNull Clause> clauses,
    @NotNull Def.Signature signature, @Nullable SourcePos resultPos
  ) {
    var res = clauses.mapIndexed((index, clause) -> {
      tracing(builder -> builder.shift(new Trace.LabelT(clause.sourcePos, "clause " + (1 + index))));
      var elabClause = visitMatch(clause, signature);
      tracing(TreeBuilder::reduce);
      return elabClause;
    });
    exprTycker.solveMetas();
    var zonker = exprTycker.newZonker();
    return Tuple.of(zonker.zonk(signature.result(), resultPos),
      res.map(c -> new Pat.PrototypeClause(
        c.sourcePos(), c.patterns().map(p -> p.zonk(zonker)),
        c.expr().map(e -> zonker.zonk(e, c.sourcePos())))));
  }

  @SuppressWarnings("unchecked") private @NotNull Pat doTyck(@NotNull Pattern pattern, @NotNull Term term) {
    return switch (pattern) {
      case Pattern.Absurd absurd -> {
        var selection = selectCtor(term, null, absurd);
        if (selection != null) {
          foundError();
          exprTycker.reporter.report(new PatternProblem.PossiblePat(absurd, selection._3));
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
        if (as != null) exprTycker.localCtx.put(as, sigma);
        yield new Pat.Tuple(tuple.explicit(),
          visitPatterns(sig, tuple.patterns().view())._1, as, sigma);
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
        var patterns = visitPatterns(sig, ctor.params().view());
        yield new Pat.Ctor(ctor.explicit(), realCtor._3.ref(), patterns._1, ctor.as(), realCtor._1);
      }
      case Pattern.Bind bind -> {
        var v = bind.bind();
        exprTycker.localCtx.put(v, term);
        yield new Pat.Bind(bind.explicit(), v, term);
      }
      case default -> throw new UnsupportedOperationException("Number and underscore patterns are unsupported yet");
    };
  }

  private Pat.PrototypeClause visitMatch(Pattern.@NotNull Clause match, Def.@NotNull Signature signature) {
    exprTycker.localCtx = exprTycker.localCtx.derive();
    currentClause = match;
    var patterns = visitPatterns(signature, match.patterns.view());
    var type = patterns._2;
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
    return new Pat.PrototypeClause(match.sourcePos, patterns._1, result);
  }

  public @NotNull Tuple2<ImmutableSeq<Pat>, Term>
  visitPatterns(Def.Signature sig, SeqView<Pattern> stream) {
    var results = DynamicSeq.<Pat>create();
    while (sig.param().isNotEmpty()) {
      var param = sig.param().first();
      Pattern pat;
      if (param.explicit()) {
        if (stream.isEmpty()) {
          foundError();
          // TODO[ice]: not enough patterns
          throw new ExprTycker.TyckerException();
        }
        pat = stream.first();
        stream = stream.drop(1);
        if (!pat.explicit()) {
          foundError();
          // TODO[ice]: too many implicit patterns
          throw new ExprTycker.TyckerException();
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
      foundError();
      exprTycker.reporter.report(new PatternProblem
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
    // TODO: implicitly generated patterns might be inferred to something else?
    var bind = new Pat.Bind(false, new LocalVar(ref.name(), ref.definition()), data.param.type());
    data.results.append(bind);
    exprTycker.localCtx.put(bind.as(), data.param.type());
    termSubst.add(ref, bind.toTerm());
    return data.sig.inst(termSubst);
  }

  private void foundError() {
    hasError = true;
    if (currentClause != null) currentClause.hasError = true;
  }

  private @NotNull Pat withError(Problem problem, Pattern pattern, Term param) {
    exprTycker.reporter.report(problem);
    foundError();
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
      exprTycker.reporter.report(new PatternProblem.SplittingOnNonData(pos, param));
      foundError();
      return null;
    }
    var core = dataCall.ref().core;
    if (core == null) {
      exprTycker.reporter.report(new NotYetTyckedError(pos.sourcePos(), dataCall.ref()));
      foundError();
      return null;
    }
    for (var ctor : core.body) {
      if (name != null && ctor.ref() != name) continue;
      var matchy = mischa(dataCall, core, ctor);
      if (matchy.isOk()) return Tuple.of(dataCall, matchy.get(), dataCall.conHead(ctor.ref()));
      // For absurd pattern, we look at the next constructor
      if (name == null) {
        // Is blocked
        if (matchy.getErr()) {
          exprTycker.reporter.report(new PatternProblem.BlockedEval(pos, dataCall));
          return null;
        }
        continue;
      }
      // Since we cannot have two constructors of the same name,
      // if the name-matching constructor mismatches the type,
      // we get an error.
      exprTycker.reporter.report(new PatternProblem.UnavailableCtor(pos, dataCall));
      foundError();
      return null;
    }
    return null;
  }

  private Result<Substituter.TermSubst, Boolean> mischa(CallTerm.Data dataCall, DataDef core, CtorDef ctor) {
    if (ctor.pats.isNotEmpty()) return PatMatcher.tryBuildSubstArgs(ctor.pats, dataCall.args());
    else return Result.ok(Unfolder.buildSubst(core.telescope(), dataCall.args()));
  }
}
