// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.unify;

import kala.collection.mutable.MutableArrayList;
import kala.collection.mutable.MutableList;
import org.aya.prettier.FindUsage;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.LamTerm;
import org.aya.syntax.core.term.SortTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.MetaCall;
import org.aya.syntax.core.term.marker.Formation;
import org.aya.syntax.ref.LocalCtx;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.ref.MetaVar;
import org.aya.tyck.TyckState;
import org.aya.tyck.error.MetaVarProblem;
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

  public @NotNull TyckState.Eqn createEqn(@NotNull MetaCall lhs, @NotNull Term rhs) {
    return new TyckState.Eqn(lhs, rhs, cmp, pos, localCtx().clone());
  }

  public @NotNull Unifier derive(@NotNull SourcePos pos, Ordering ordering) {
    return new Unifier(state, localCtx().derive(), reporter, pos, ordering, allowDelay);
  }

  @Override protected @Nullable Term doSolveMeta(@NotNull MetaCall meta, @NotNull Term rhs, @Nullable Term type) {
    if (rhs instanceof MetaCall rMeta && rMeta.ref() == meta.ref())
      return sameMeta(meta, type, rMeta);
    // Assumption: rhs is in whnf
    var spine = meta.args();

    var inverted = MutableArrayList.<LocalVar>create(spine.size());
    var overlap = MutableList.<LocalVar>create();
    var wantToReturn = false;
    for (var arg : spine) {
      // TODO: apply uneta
      if (whnf(arg) instanceof FreeTerm(var var)) {
        if (inverted.contains(var)) overlap.append(var);
        inverted.append(var);
      } else if (allowVague) {
        inverted.append(LocalVar.generate("_"));
      } else if (allowDelay) {
        state.addEqn(createEqn(meta, rhs));
        wantToReturn = true;
        break;
      } else {
        reportBadSpine(meta, rhs);
        return null;
      }
    }

    var returnType = computeReturnType(meta, rhs, type);
    if (wantToReturn) return returnType;
    if (returnType == null) return null;

    // In this case, the solution may not be unique (see #608),
    // so we may delay its resolution to the end of the tycking when we disallow delayed unification.
    var tmpRhs = rhs; // to get away with Java warning
    if (!allowVague && overlap.anyMatch(var -> FindUsage.free(tmpRhs, var) > 0)) {
      if (allowDelay) {
        state.addEqn(createEqn(meta, rhs));
        return returnType;
      } else {
        reportBadSpine(meta, rhs);
        return null;
      }
    }
    // Now we are sure that the variables in overlap are all unused.

    var findUsage = FindUsage.unfree(rhs, inverted);
    if (findUsage.termUsage > 0) {
      rhs = fullNormalize(rhs);
      findUsage = FindUsage.unfree(rhs, inverted);
    }
    if (findUsage.termUsage > 0) {
      fail(new MetaVarProblem.BadlyScopedError(meta, rhs, inverted));
      return null;
    }
    if (findUsage.metaUsage > 0) {
      if (allowDelay) {
        state.addEqn(createEqn(meta, rhs));
        return returnType;
      } else {
        fail(new MetaVarProblem.BadlyScopedError(meta, rhs, inverted));
        return null;
      }
    }
    var ref = meta.ref();
    if (FindUsage.meta(rhs, ref) > 0) {
      fail(new MetaVarProblem.RecursionError(meta, rhs));
      return null;
    }
    var candidate = rhs.bindTele(inverted.view());
    // It might have extra arguments, in those cases we need to abstract them out.
    solve(ref, LamTerm.make(spine.size() - ref.ctxSize(), candidate));
    return returnType;
  }

  /** The "flex-flex" case with identical meta ref */
  private @Nullable Term sameMeta(@NotNull MetaCall meta, @Nullable Term type, MetaCall rMeta) {
    if (meta.args().size() != rMeta.args().size()) return null;
    for (var i = 0; i < meta.args().size(); i++) {
      if (!compare(meta.args().get(i), rMeta.args().get(i), null)) {
        return null;
      }
    }
    return type;
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

  private void reportBadSpine(@NotNull MetaCall meta, @NotNull Term rhs) {
    fail(new MetaVarProblem.BadSpineError(meta, state, rhs));
  }
  private void reportIllTyped(@NotNull MetaCall meta, @NotNull Term rhs) {
    fail(new MetaVarProblem.IllTypedError(meta, state, rhs));
  }
}
