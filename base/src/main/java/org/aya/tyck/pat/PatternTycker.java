// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Result;
import kala.value.MutableValue;
import org.aya.generic.Constants;
import org.aya.generic.Renamer;
import org.aya.generic.State;
import org.aya.generic.term.DTKind;
import org.aya.normalize.Normalizer;
import org.aya.syntax.compile.JitCon;
import org.aya.syntax.compile.JitData;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.concrete.stmt.decl.DataDecl;
import org.aya.syntax.core.Jdg;
import org.aya.syntax.core.annotation.Bound;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.def.*;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.pat.PatMatcher;
import org.aya.syntax.core.pat.PatToTerm;
import org.aya.syntax.core.repr.AyaShape;
import org.aya.syntax.core.repr.CodeShape;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.ConCallLike;
import org.aya.syntax.core.term.call.DataCall;
import org.aya.syntax.ref.LocalVar;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.TyckState;
import org.aya.tyck.ctx.LocalLet;
import org.aya.tyck.error.PatternProblem;
import org.aya.tyck.pat.iter.PatternIterator;
import org.aya.tyck.pat.iter.Pusheenable;
import org.aya.tyck.pat.iter.SignatureIterator;
import org.aya.tyck.tycker.Problematic;
import org.aya.tyck.tycker.Stateful;
import org.aya.util.Arg;
import org.aya.util.Panic;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Tyck for {@link Pattern}'s, the left hand side of one clause.
 */
public class PatternTycker implements Problematic, Stateful {
  private final @NotNull ExprTycker exprTycker;
  private final boolean allowImplicit;
  /// A bound telescope (i.e. all the reference to the former parameter are
  /// [org.aya.syntax.core.term.LocalTerm])
  private final @NotNull SignatureIterator telescope;

  /// Substitution for parameter, in the same order as parameter
  private final @NotNull MutableList<@Closed Jdg> paramSubst;

  /// Substitution for `as` pattern.
  /// Assertion: `asSubst.parent == null`
  private final @NotNull LocalLet asSubst;

  /// Almost equivalent to {@code telescope.peek()}, but we may instantiate it.
  ///
  /// @see #instCurrentParam()
  private @UnknownNullability Param currentParam = null;
  private boolean hasError = false;
  private final @NotNull Renamer nameGen;

  /**
   * @see #tyckInner(ImmutableSeq, ImmutableSeq, WithPos)
   */
  private PatternTycker(
    @NotNull ExprTycker tycker,
    @NotNull SignatureIterator tele,
    @NotNull LocalLet sub,
    @NotNull Renamer nameGen
  ) {
    this(tycker, tele, sub, true, nameGen);
  }

  public PatternTycker(
    @NotNull ExprTycker exprTycker,
    @NotNull SignatureIterator telescope,
    @NotNull LocalLet asSubst,
    boolean allowImplicit,
    @NotNull Renamer nameGen
  ) {
    this.exprTycker = exprTycker;
    this.telescope = telescope;
    this.paramSubst = MutableList.create();
    this.asSubst = asSubst;
    this.allowImplicit = allowImplicit;
    this.nameGen = nameGen;
    nameGen.store(exprTycker.localCtx());
  }

  public record TyckResult(
    @NotNull ImmutableSeq<@Closed Pat> wellTyped,
    @NotNull ImmutableSeq<@Closed Jdg> paramSubst,
    @NotNull LocalLet asSubst,
    boolean hasError
  ) { }

  /**
   * Tyck a {@param type} against {@param type}
   *
   * @param pattern a concrete {@link Pattern}
   * @param type    the type of {@param pattern}, it probably contains {@link MetaPatTerm}
   * @return a well-typed {@link Pat}, but still need to be inline!
   */
  private @NotNull @Closed Pat doTyck(@NotNull WithPos<Pattern> pattern, @NotNull @Closed Term type) {
    return switch (pattern.data()) {
      case Pattern.Absurd _ -> {
        var selection = makeSureEmpty(type, pattern);
        if (selection != null) {
          foundError(new PatternProblem.PossiblePat(pattern, selection));
        }
        yield Pat.Misc.Absurd;
      }
      case Pattern.Tuple(var l, var r) -> {
        if (!(exprTycker.whnf(type) instanceof DepTypeTerm(var kind, var lT, var rT) && kind == DTKind.Sigma)) {
          var frozen = freezeHoles(type);
          yield withError(new PatternProblem.TupleNonSig(pattern, this, frozen), frozen);
        }
        var lhs = doTyck(l, lT);
        yield new Pat.Tuple(lhs, doTyck(r, rT.apply(PatToTerm.visit(lhs))));
      }
      case Pattern.Con con -> {
        var realCon = makeSureAvail(type, con.resolved().data(), pattern);
        if (realCon == null) yield randomPat(type);
        var conCore = realCon.conHead.ref();

        // It is possible that `con.params()` is empty.
        var patterns = tyckInner(
          conCore.selfTele(realCon.ownerArgs),
          con.params(),
          pattern);

        // check if this Con is a ShapedCon
        var typeRecog = state().shapeFactory.find(conCore.dataRef()).getOrNull();
        yield new Pat.Con(patterns, realCon.conHead);
      }
      case Pattern.Bind bindPat -> {
        var bind = bindPat.bind();
        var tyRef = bindPat.theCoreType();

        exprTycker.localCtx().put(bind, type);
        tyRef.set(type);

        // report after tyRef.set, the error message requires it
        if (whnf(type) instanceof DataCall call) {
          var unimportedCon = collectNoParamConNames(call.ref())
            .anyMatch(it -> it.equalsIgnoreCase(bind.name()));
          if (unimportedCon) {
            fail(new PatternProblem.UnimportedConName(pattern.replace(bindPat)));
          }
        }

        yield new Pat.Bind(bind, type);
      }
      case Pattern.CalmFace.INSTANCE -> doGeneratePattern(type, Constants.ANONYMOUS_PREFIX, pattern.sourcePos());
      case Pattern.Number(var number) -> {
        var ty = whnf(type);
        if (ty instanceof DataCall dataCall) {
          var data = dataCall.ref();
          var shape = state().shapeFactory.find(data);
          if (shape.isDefined() && shape.get().shape() == AyaShape.NAT_SHAPE)
            yield new Pat.ShapedInt(number,
              shape.get().getCon(CodeShape.GlobalId.ZERO),
              shape.get().getCon(CodeShape.GlobalId.SUC),
              dataCall);
        }
        yield withError(new PatternProblem.BadLitPattern(pattern, ty), ty);
      }
      case Pattern.List(var el) -> {
        // desugar `Pattern.List` to `Pattern.Con` here, but use `CodeShape` !
        // Note: this is a special case (maybe), If there is another similar requirement,
        //       a PatternDesugarer is recommended.
        var ty = whnf(type);
        if (ty instanceof DataCall dataCall) {
          var data = dataCall.ref();
          var shape = state().shapeFactory.find(data).getOrNull();
          if (shape != null && shape.shape() == AyaShape.LIST_SHAPE)
            yield doTyck(new Pattern.FakeShapedList(pattern.sourcePos(), el,
              shape.getCon(CodeShape.GlobalId.NIL), shape.getCon(CodeShape.GlobalId.CONS), dataCall)
              .constructorForm(), type);
        }
        yield withError(new PatternProblem.BadLitPattern(pattern, ty), ty);
      }
      case Pattern.As(var inner, var as, var typeRef) -> {
        var innerPat = doTyck(inner, type);

        typeRef.set(type);
        addAsSubst(as, innerPat, type);
        // exprTycker.localCtx().put(as, type);

        yield innerPat;
      }
      case Pattern.Salt _ -> Panic.unreachable();
    };
  }

  private void peekNextParam() {
    if (telescope.hasNext()) currentParam = telescope.peek();
    else currentParam = null;
  }

  private void consumeParam() {
    telescope.next();
  }

  private record FindNextParam(@NotNull ImmutableSeq<@Closed Pat> generated, @NotNull Kind kind) {
    public enum Kind {
      Success, TooManyPattern, TooManyImplicit
    }
  }

  /// Find next param until the predicate success.
  ///
  /// @return (generated implicit patterns, status)
  /// @apiNote before call: {@link #currentParam} is the last checked parameter
  ///          after call: {@link #currentParam} is the first unchecked parameter which {@param until} success on
  private @NotNull FindNextParam findNextParam(@Nullable WithPos<Pattern> pattern, @NotNull Predicate<Param> until) {
    var generatedPats = MutableList.<@Closed Pat>create();

    peekNextParam();
    // loop invariant: currentParam is the first unchecked parameter if not null
    while (currentParam != null && !until.test(currentParam)) {
      if (currentParam.explicit()) {
        // too many implicit
        try (var _ = instCurrentParam()) {
          assert pattern != null;
          foundError(new PatternProblem.TooManyImplicitPattern(pattern, currentParam));
        }

        return new FindNextParam(generatedPats.toSeq(), FindNextParam.Kind.TooManyImplicit);
      } else {
        // current param is implicit, generate pattern for it
        generatedPats.append(generatePattern());
        // we don't need to consume the parameter, cause [generatePattern] does.
        // TODO: ^is it good?
      }

      // we consumed the last checked parameter, now look at next one
      peekNextParam();
    }

    // no more param
    if (currentParam == null) {
      return new FindNextParam(generatedPats.toSeq(), FindNextParam.Kind.TooManyPattern);
    }

    return new FindNextParam(generatedPats.toSeq(), FindNextParam.Kind.Success);
  }

  public @NotNull TyckResult tyck(
    @NotNull PatternIterator patterns,
    @Nullable WithPos<Pattern> outerPattern
  ) {
    var wellTyped = MutableList.<@Closed Pat>create();
    // last user given pattern, that is, not aya generated
    @Nullable Arg<WithPos<Pattern>> lastPat = null;

    // loop invariant: [patterns] points to the last checked pattern, same for [telescope]
    // "points to" means the last element it consumes holds the property.
    outer:
    while (patterns.hasNext()) {
      var currentPat = patterns.peek();

      lastPat = currentPat;

      if (!currentPat.explicit() && !allowImplicit) {
        foundError(new PatternProblem.ImplicitDisallowed(currentPat.term()));
        return done(wellTyped);
      }

      // find the next appropriate parameter
      var fnp = findNextParam(currentPat.term(), p ->
        p.explicit() == currentPat.explicit());
      // || telescope.isFromPusheen() == patterns.isFromPusheen()
      // ^this check is implied by the first one

      ImmutableSeq<Pat> generated = null;

      switch (fnp.kind) {
        case Success -> generated = fnp.generated;
        case TooManyPattern -> {
          if (patterns.isFromPusheen()) {
            // It is fine if a pusheen pattern mismatch
            wellTyped.appendAll(fnp.generated);
            break outer;
          } else {
            // no more parameters
            foundError(new PatternProblem.TooManyPattern(currentPat.term()));
          }
        }
      }

      if (generated == null) {
        return done(wellTyped);
      }

      wellTyped.appendAll(generated);

      // avoid unnecessary pusheen
      if (patterns.isFromPusheen() && telescope.isFromPusheen()) {
        break;
      }

      wellTyped.append(tyckPattern(currentPat.term()));   // tyckPattern consumes [currentParam] by move [telescope] forward
      patterns.next();    // consume pattern

      // now: [patterns] points to the last checked pattern, same for [telescope]
    }

    // now: ! patterns.hasNext()
    // or patterns.hasNext() && ! telescope.hasNext() && patterns.isFromPusheen()
    // or patterns.hasNext() && telescope.hasNext() && patterns.isFromPusheen() && telescope.isFromPusheen()

    // all not pusheen patterns have their parameters

    // is there any explicit parameters?
    // this process also generate pattern for tailing implicit parameters
    var generated = findNextParam(null, p ->
      p.explicit() || telescope.isFromPusheen());
    // ^this check implies the first one

    // what kind of parameter you found?
    if (generated.kind == FindNextParam.Kind.Success && !telescope.isFromPusheen()) {
      // [findNextParam] success means at least one parameter is peeked,
      // thus `telescope.hasNext()` is true before the call to `findNextParam`.
      // Therefore, now: ! patterns.hasNext(),
      // other two cases are ruled out by `telescope.hasNext()` and `! telescope.isFromPusheen()`

      // no you can't!
      WithPos<Pattern> errorPattern = lastPat == null
        ? Objects.requireNonNull(outerPattern)
        : lastPat.term();
      try (var _ = instCurrentParam()) {
        foundError(new PatternProblem.InsufficientPattern(errorPattern, currentParam));
      }
      return done(wellTyped);
    }

    // it is impossible that [generated.kind] is [FindNextParam.TooManyImplicit]
    assert generated.kind != FindNextParam.Kind.TooManyImplicit;

    wellTyped.appendAll(generated.generated);

    // [currentParam] = null or [telescope.isFromPusheen()]
    return done(wellTyped);
  }

  /// Must be called before using [#currentParam]
  private @NotNull Closer instCurrentParam() {
    currentParam = currentParam.descent(t -> t.instTele(paramSubst.view().map(Jdg::wellTyped)));
    return CLOSER;
  }

  private final Closer CLOSER = new Closer();
  private class Closer implements AutoCloseable {
    @Override public void close() { consumeParam(); }
  }

  /**
   * Checking {@param pattern} with {@link PatternTycker#currentParam}
   */
  private @NotNull @Closed Pat tyckPattern(@NotNull WithPos<Pattern> pattern) {
    try (var _ = instCurrentParam()) {
      var result = doTyck(pattern, currentParam.type());
      addArgSubst(result, currentParam.type());
      return result;
    }
  }

  private @NotNull @Closed Pat doGeneratePattern(@NotNull @Closed Term type, @NotNull String name, @NotNull SourcePos pos) {
    var freshVar = nameGen.bindName(name);
    if (exprTycker.whnf(type) instanceof DataCall dataCall) {
      // this pattern would be a Con, it can be inferred
      // the db-closeness of Pat.Meta is determined by [dataCall], not its solution.
      return new Pat.Meta(MutableValue.create(), freshVar.name(), dataCall, pos);
    } else {
      // If the type is not a DataCall, then the only available pattern is Pat.Bind
      exprTycker.localCtx().put(freshVar, type);
      return new Pat.Bind(freshVar, type);
    }
  }

  /**
   * For every implicit parameter which is not explicitly (not user given pattern) matched,
   * we generate a MetaPat for each,
   * so that they can be inferred during {@link ClauseTycker}
   */
  private @NotNull @Closed Pat generatePattern() {
    try (var _ = instCurrentParam()) {
      // TODO: I NEED A SOURCE POS!!
      var pat = doGeneratePattern(currentParam.type(), currentParam.name(), SourcePos.NONE);
      addArgSubst(pat, currentParam.type());
      return pat;
    }
  }

  private @NotNull ImmutableSeq<Pat> tyckInner(
    @NotNull ImmutableSeq<@Bound Param> telescope,
    @NotNull ImmutableSeq<Arg<WithPos<Pattern>>> patterns,
    @NotNull WithPos<Pattern> outerPattern
  ) {
    var sub = new PatternTycker(exprTycker, new SignatureIterator(telescope, new Pusheenable.Const<>(ErrorTerm.DUMMY), null), asSubst, nameGen);
    var tyckResult = sub.tyck(new PatternIterator(patterns), outerPattern);

    hasError = hasError || sub.hasError;
    return tyckResult.wellTyped;
  }

  private void addArgSubst(@NotNull @Closed Pat pattern, @NotNull @Closed Term type) {
    paramSubst.append(new Jdg.Default(PatToTerm.visit(pattern), type));
  }

  private void addAsSubst(@NotNull LocalVar as, @NotNull Pat pattern, @NotNull @Closed Term type) {
    asSubst.put(as, new Jdg.Default(PatToTerm.visit(pattern), type), false);
  }

  private @NotNull TyckResult done(@NotNull MutableList<@Closed Pat> wellTyped) {
    return new TyckResult(wellTyped.toSeq(), paramSubst.toSeq(), asSubst, hasError);
  }

  private record Selection(
    @NotNull @Closed DataCall data,
    @NotNull ImmutableSeq<@Closed Term> ownerArgs,
    @NotNull ConCallLike.@Closed Head conHead
  ) { }

  private @Nullable ConCallLike.Head makeSureEmpty(@Closed Term type, @NotNull WithPos<Pattern> pattern) {
    if (!(exprTycker.whnf(type) instanceof DataCall dataCall)) {
      foundError(new PatternProblem.SplittingOnNonData(pattern, type));
      return null;
    }

    var core = dataCall.ref();

    // If name != null, only one iteration of this loop is not skipped
    for (var con : core.body()) {
      switch (checkAvail(dataCall, con, exprTycker.state)) {
        case Result.Ok(var subst) -> {
          return new ConCallLike.Head(con, dataCall.ulift(), subst);
        }
        // Is blocked
        case Result.Err(var st) when st == State.Stuck -> {
          foundError(new PatternProblem.BlockedEval(pattern, dataCall));
          return null;
        }
        default -> { }
      }
    }
    return null;
  }

  private @Nullable Selection makeSureAvail(@Closed Term type, @NotNull ConDefLike name, @NotNull WithPos<Pattern> pattern) {
    if (!(exprTycker.whnf(type) instanceof DataCall dataCall)) {
      foundError(new PatternProblem.SplittingOnNonData(pattern, type));
      return null;
    }
    if (!name.dataRef().equals(dataCall.ref())) {
      foundError(new PatternProblem.UnknownCon(pattern));
      return null;
    }

    return switch (checkAvail(dataCall, name, exprTycker.state)) {
      case Result.Ok(var subst) -> new Selection(
        dataCall, subst,
        new ConCallLike.Head(name, dataCall.ulift(), subst));
      case Result.Err(_) -> {
        // Here, name != null, and is not in the list of checked body
        foundError(new PatternProblem.UnavailableCon(pattern, dataCall));
        yield null;
      }
    };
  }

  private static @NotNull SeqView<String> collectNoParamConNames(@NotNull DataDefLike call) {
    return switch (call) {
      case JitData jitData -> jitData.body().view()
        .filter(it -> it.selfTeleSize() == 0)
        .map(AnyDef::name);
      case DataDef.Delegate delegate -> {
        // the core may be unchecked!
        var concrete = (DataDecl) delegate.ref.concrete;
        yield concrete.body.clauses.view()
          .filter(it -> it.telescope.isEmpty())
          .map(it -> it.ref.name());
      }
    };
  }

  /**
   * Check whether {@param con} is available under {@param type}
   */
  public static @NotNull Result<ImmutableSeq<Term>, State> checkAvail(
    @NotNull @Closed DataCall type, @NotNull ConDefLike con, @NotNull TyckState state
  ) {
    return switch (con) {
      case JitCon jitCon -> jitCon.isAvailable(new Normalizer(state), type.args());
      case ConDef.Delegate conDef -> {
        var pats = conDef.core().pats;
        if (pats.isNotEmpty()) {
          var matcher = new PatMatcher.InferMeta(new Normalizer(state));
          yield matcher.apply(pats, type.args());
        }

        yield Result.ok(type.args());
      }
    };
  }

  private @NotNull Pat randomPat(@Closed Term param) {
    return new Pat.Bind(nameGen.bindName(param), param);
  }

  // region Error Reporting
  @Override public @NotNull Reporter reporter() { return exprTycker.reporter; }
  @Override public @NotNull TyckState state() { return exprTycker.state; }

  private @NotNull Pat withError(Problem problem, Term param) {
    foundError(problem);
    // In case something's wrong, produce a random pattern
    return randomPat(param);
  }

  private void foundError(@Nullable Problem problem) {
    hasError = true;
    if (problem != null) fail(problem);
  }
  // endregion Error Reporting
}
