// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.normalize;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.ImmutableSet;
import kala.control.Either;
import kala.control.Result;
import org.aya.generic.Modifier;
import org.aya.states.TyckState;
import org.aya.syntax.compile.JitFn;
import org.aya.syntax.compile.JitMatchy;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.def.FnDef;
import org.aya.syntax.core.def.Matchy;
import org.aya.syntax.core.pat.PatMatcher;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.*;
import org.aya.syntax.core.term.marker.BetaRedex;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.aya.syntax.core.term.repr.MetaLitTerm;
import org.aya.syntax.core.term.xtt.CoeTerm;
import org.aya.syntax.core.term.xtt.DimTerm;
import org.aya.syntax.core.term.xtt.EqTerm;
import org.aya.syntax.literate.CodeOptions.NormalizeMode;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.LocalVar;
import org.aya.tyck.tycker.Stateful;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

import static org.aya.generic.State.Stuck;

/**
 * Unlike in pre-v0.30 Aya, we use only one normalizer, only doing head reduction,
 * and we merge conservative normalizer and the whnf normalizer.
 * <p>
 * Even though it has a field {@link #state}, do not make it extend {@link Stateful},
 * because there is a method called whnf in it, which clashes with the one here.
 */
public final class Normalizer implements UnaryOperator<Term> {
  public final @NotNull TyckState state;
  public @NotNull ImmutableSet<AnyVar> opaque = ImmutableSet.empty();
  private boolean fullNormalize = false;
  public Normalizer(@NotNull TyckState state) { this.state = state; }

  /**
   * This function is tail-recursion optimized.
   * To tail-recursively call `apply`, assign `term` with the result and `continue`.
   */
  @SuppressWarnings("UnnecessaryContinue") @Override
  public @Closed @NotNull Term apply(@Closed @NotNull Term term) {
    while (true) {
      var alreadyWHNF = term instanceof StableWHNF ||
        term instanceof FreeTerm ||
        // ConCall for point constructors are always in WHNF
        (term instanceof ConCall con && !con.ref().hasEq());
      if (alreadyWHNF && !fullNormalize) return term;

      switch (term) {
        case FreeTerm _, SortTerm _ -> {
          return term;
        }
        case LetFreeTerm(var _, var definedAs) -> {
          term = definedAs.wellTyped();
          continue;
        }
        // Already full NF mode
        // Although normalizing a LamTerm looks very bad, but it is required due to our elaboration for partial application:
        // For `f : A -> B -> C` and `a : A`, we will elaborate `f a` as `fn b => f a b`. If `f a` can be normalized, then
        // we hope `fn b => f a b` can be normalized too.
        case LamTerm(var lam) -> {
          return new LamTerm(lam.reapply(this));
        }
        case EqTerm(@Closed var A, @Closed var a, @Closed var b) -> {
          return new EqTerm(A.reapply(this), apply(a), apply(b));
        }
        case DepTypeTerm(@Closed var kk, @Closed var param, @Closed var body) -> {
          return new DepTypeTerm(kk, apply(param), body.reapply(this));
        }
        case LetTerm(@Closed var definedAs, @Closed var body) -> {
          term = body.apply(apply(definedAs));
          continue;
        }
        // Already full NF mode
        // Make sure you handle all [Term]s that contains [Closure] before, such as [LamTerm]
        case StableWHNF _ -> {
          return term.descent(this);
        }
        case LocalTerm _ -> throw new IllegalStateException("Local term escapes: " + term);
        case BetaRedex app -> {
          var result = app.descent(this);
          if (result == app) return app;
          if (!(result instanceof BetaRedex newApp)) {
            term = result;
            continue;
          } else {
            // Try again and see if it gets stuck
            var tryMake = newApp.make();
            if (tryMake != newApp) {
              term = tryMake;
              continue;
            } else {
              return newApp;
            }
          }
        }
        case FnCall(JitFn instance, int ulift, var args, var tc) -> {
          args = Callable.descent(args, this);
          var result = instance.invoke(this, args);
          if (result instanceof FnCall(var ref, _, var newArgs, _) && ref == instance) {
            if (newArgs.sameElements(args, true)) return term;
            if (fullNormalize) return new FnCall(ref, ulift, args, tc);
          }
          term = result.elevate(ulift);
          continue;
        }
        case FnCall(FnDef.Delegate delegate, int ulift, var args, var tc) -> {
          var whnfArgs = Callable.descent(args, this);
          FnDef core = delegate.core();
          if (core == null) {
            return fullNormalize ? new FnCall(delegate, ulift, whnfArgs, tc) : term;
          }
          if (!isOpaque(core)) switch (core.body()) {
            case Either.Left(var body): {
              term = body.instTele(whnfArgs.view());
              continue;
            }
            case Either.Right(var body): {
              var result = tryUnfoldClauses(body.matchingsView(),
                whnfArgs, core.is(Modifier.Overlap), ulift);
              // we may get stuck
              if (result == null) {
                if (args.sameElements(whnfArgs, true)) return term;
                return new FnCall(delegate, ulift, whnfArgs, tc);
              }
              term = result;
              continue;
            }
          }
          return term;
        }
        case RuleReducer rule -> {
          var newArgs = Callable.descent(rule.args(), this);
          var reduction = rule.rule().apply(newArgs);
          if (reduction != null) {
            term = reduction;
            continue;
          }
          // We can't handle it, try to delegate to FnCall
          switch (rule) {
            case RuleReducer.Fn fn -> {
              @Closed var fnCall = new FnCall(fn.rule().ref(), fn.ulift(), newArgs);
              term = apply(fnCall);
              if (term == fnCall) return rule;
              continue;
            }
            case RuleReducer.Con _ -> {
              return term;
            }
          }
        }
        case @Closed ConCall call -> {
          if (call.ref().hasEq() && apply(call.conArgs().getLast()) instanceof DimTerm dim) {
            var args = Callable.descent(call.args(), this);
            term = call.head().ref().equality(args, dim == DimTerm.I0);
            continue;
          }
          // Already in fullNormalize mode
          return call.descent(this);
        }
        case PrimCall prim -> {
          var newArgs = Callable.descent(prim.args(), this);
          if (newArgs.sameElements(prim.args(), true))
            return prim;
          prim = new PrimCall(prim.ref(), prim.ulift(), newArgs);
          return state.primFactory.unfold(prim, state);
        }
        case MetaPatTerm meta -> {
          term = meta.inline(this);
          if (meta == term) return meta;
          continue;
        }
        case MetaCall meta -> {
          term = state.computeSolution(meta, this);
          if (meta == term) return meta;
          continue;
        }
        case MetaLitTerm meta -> {
          return meta.inline(this);
        }
        case @Closed CoeTerm coe -> {
          var r = apply(coe.r());
          var s = apply(coe.s());
          @Closed var A = coe.type();
          if (state.isConnected(r, s)) return LamTerm.ID;

          var i = new LocalVar("i");
          switch (apply(A.apply(i))) {
            case DepTypeTerm dep -> {
              term = dep.coe(i, coe);
              continue;
            }
            case SortTerm _ -> {
              return LamTerm.ID;
            }
            // TODO: when the data is not indexed, also return ID
            case DataCall data when data.args().isEmpty() -> {
              return LamTerm.ID;
            }
            default -> {
              if (r == coe.r() && s == coe.s()) return coe;
              if (fullNormalize) return new CoeTerm(A, r, s);
              return coe;
            }
          }
        }
        case MatchCall(Matchy clauses, var discr, var captures) -> {
          var whnfDiscr = Callable.descent(discr, this);
          var result = tryUnfoldClauses(clauses.clauses().view(), whnfDiscr, false,
            (discrSubst, body) ->
              body.instTele(captures.view().concat(discrSubst)));
          if (result == null) {
            if (discr.sameElements(whnfDiscr, true)) return term;
            if (fullNormalize) return new MatchCall(clauses, whnfDiscr, captures);
            return term;
          }
          term = result;
          continue;
        }
        case MatchCall(JitMatchy fn, var discr, var captures) -> {
          discr = Callable.descent(discr, this);
          var result = fn.invoke(this, captures, discr);
          if (result instanceof MatchCall(var ref, var newDiscr, var newCaptures) && ref == fn) {
            if (newDiscr.sameElements(discr, true) &&
              newCaptures.sameElements(captures, true))
              return term;
            if (fullNormalize) return new MatchCall(ref, discr, captures);
            return term;
          }
          term = result;
          continue;
        }
      }
    }
  }

  private boolean isOpaque(@NotNull FnDef fn) {
    return opaque.contains(fn.ref()) || fn.is(Modifier.Opaque) || fn.is(Modifier.NonTerminating);
  }

  public @Nullable Term tryUnfoldClauses(
    @NotNull SeqView<Term.Matching> clauses, @NotNull ImmutableSeq<Term> args,
    boolean orderIndependent, BiFunction<ImmutableSeq<Term>, Term, Term> onSuccess
  ) {
    for (var matchy : clauses) {
      var matcher = new PatMatcher.NoMeta(this);
      switch (matcher.apply(matchy.patterns(), args)) {
        case Result.Err(var st) -> {
          if (!orderIndependent && st == Stuck) return null;
        }
        case Result.Ok(var subst) -> {
          return onSuccess.apply(subst, matchy.body());
        }
      }
    }
    return null;
  }

  public @Nullable Term tryUnfoldClauses(
    @NotNull SeqView<Term.Matching> clauses, @NotNull ImmutableSeq<Term> args,
    boolean orderIndependent, int ulift
  ) {
    return tryUnfoldClauses(clauses, args, orderIndependent, (subst, body) ->
      body.elevate(ulift).instTele(subst.view()));
  }

  private class Full implements UnaryOperator<Term> {
    { fullNormalize = true; }

    @Override public @Closed @NotNull Term apply(@Closed @NotNull Term term) { return Normalizer.this.apply(term); }
  }

  /// Do NOT use this in the type checker.
  /// This is for REPL/literate mode and testing.
  public @Closed @NotNull Term normalize(@Closed @NotNull Term term, NormalizeMode mode) {
    return switch (mode) {
      case HEAD -> apply(term);
      case FULL -> new Full().apply(term);
      case NULL -> new Finalizer.Freeze(() -> state).zonk(term);
      case null -> new Finalizer.Freeze(() -> state).zonk(term);
    };
  }
}
