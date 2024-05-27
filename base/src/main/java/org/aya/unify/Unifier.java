// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.unify;

import kala.collection.mutable.MutableArrayList;
import kala.collection.mutable.MutableList;
import org.aya.prettier.FindUsage;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.MetaCall;
import org.aya.syntax.core.term.marker.Formation;
import org.aya.syntax.ref.LocalCtx;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.ref.MetaVar;
import org.aya.tyck.TyckState;
import org.aya.tyck.error.HoleProblem;
import org.aya.util.Ordering;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Unifier extends TermComparator {
  private final boolean allowDelay;
  public boolean allowVague = false;
  public Unifier(
    @NotNull TyckState state, @NotNull LocalCtx ctx,
    @NotNull Reporter reporter, @NotNull SourcePos pos, @NotNull Ordering cmp,
    boolean allowDelay
  ) {
    super(state, ctx, reporter, pos, cmp);
    this.allowDelay = allowDelay;
  }

  public @NotNull TyckState.Eqn createEqn(@NotNull Term lhs, @NotNull Term rhs) {
    return new TyckState.Eqn(lhs, rhs, cmp, pos, localCtx().clone());
  }

  public @NotNull Unifier derive(@NotNull SourcePos pos, Ordering ordering) {
    return new Unifier(state, localCtx().derive(), reporter, pos, ordering, allowDelay);
  }

  @Override protected @Nullable Term doSolveMeta(@NotNull MetaCall meta, @NotNull Term rhs, @Nullable Term type) {
    // Assumption: rhs is in whnf
    var spine = meta.args();

    var inverted = MutableArrayList.<LocalVar>create(spine.size());
    var overlap = MutableList.<LocalVar>create();
    for (var arg : spine) {
      // TODO: apply uneta
      if (whnf(arg) instanceof FreeTerm(var var)) {
        if (inverted.contains(var)) overlap.append(var);
        inverted.append(var);
      } else {
        reportBadSpine(meta);
        return null;
      }
    }

    var returnType = computeReturnType(meta, rhs, type);
    if (returnType == null) return null;

    // In this case, the solution may not be unique (see #608),
    // so we may delay its resolution to the end of the tycking when we disallow delayed unification.
    if (!allowVague && overlap.anyMatch(var -> FindUsage.free(rhs, var) > 0)) {
      if (allowDelay) {
        state.addEqn(createEqn(meta, rhs));
        return returnType;
      } else {
        reportBadSpine(meta);
        return null;
      }
    }
    // Now we are sure that the variables in overlap are all unused.

    var candidate = rhs.bindTele(inverted.view());
    var findUsage = FindUsage.anyFree(candidate);
    if (findUsage.termUsage > 0) {
      fail(new HoleProblem.BadlyScopedError(meta, rhs, inverted));
      return null;
    }
    if (findUsage.metaUsage > 0) {
      if (allowDelay) {
        state.addEqn(createEqn(meta, rhs));
        return returnType;
      } else {
        fail(new HoleProblem.BadlyScopedError(meta, rhs, inverted));
        return null;
      }
    }
    var ref = meta.ref();
    if (FindUsage.meta(candidate, ref) > 0) {
      fail(new HoleProblem.RecursionError(meta, candidate));
      return null;
    }
    // It might have extra arguments, in those cases we need to abstract them out.
    solve(ref, LamTerm.make(spine.size() - ref.ctxSize(), candidate));
    return returnType;
  }

  /**
   * @return null if ill-typed
   */
  private @Nullable Term computeReturnType(@NotNull MetaCall meta, @NotNull Term rhs, @Nullable Term type) {
    var needUnify = true;
    var returnType = type;
    var ref = meta.ref();
    // Running double checker is important, see #327 last task (`coe'`)
    var checker = new DoubleChecker(derive(ref.pos(), cmp));
    switch (ref.req()) {
      case MetaVar.Misc.Whatever -> needUnify = false;
      case MetaVar.Misc.IsType -> {
        switch (rhs) {
          case Formation _ -> { }
          case MetaCall rMeta -> {
            if (!checker.synthesizer().isTypeMeta(rMeta.ref().req())) {
              reportIllTyped(meta, rhs);
              return null;
            }
          }
          default -> {
            var synthesize = checker.synthesizer().trySynth(rhs);
            if (!(synthesize instanceof SortTerm)) {
              reportIllTyped(meta, rhs);
              return null;
            }
            if (returnType == null) returnType = synthesize;
          }
        }
        needUnify = false;
      }
      case MetaVar.OfType(var target) -> {
        target = MetaCall.appType(ref, target, meta.args());
        if (type != null && !compare(type, target, null)) {
          reportIllTyped(meta, rhs);
          return null;
        }
        returnType = freezeHoles(target);
      }
      case MetaVar.PiDom(var sort) -> {
        if (!checker.synthesizer().inheritPiDom(rhs, sort)) reportIllTyped(meta, rhs);
      }
    }
    if (needUnify) {
      // Check the solution.
      if (returnType != null) {
        // resultTy might be an ErrorTerm, what to do?
        if (!checker.inherit(rhs, returnType))
          reportIllTyped(meta, rhs);
      } else {
        returnType = checker.synthesizer().trySynth(rhs);
        if (returnType == null) {
          throw new UnsupportedOperationException("TODO: add an error report for this");
        }
      }
    }
    if (!needUnify && returnType == null) return SortTerm.Type0;
    else return returnType;
  }

  private void reportBadSpine(@NotNull MetaCall meta) {
    fail(new HoleProblem.BadSpineError(meta));
  }
  private void reportIllTyped(@NotNull MetaCall meta, @NotNull Term rhs) {
    fail(new HoleProblem.IllTypedError(meta, state, rhs));
  }
}
