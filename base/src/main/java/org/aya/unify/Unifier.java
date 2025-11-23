// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.unify;

import kala.collection.mutable.MutableArrayList;
import kala.collection.mutable.MutableList;
import org.aya.prettier.FindUsage;
import org.aya.states.TyckState;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.LamTerm;
import org.aya.syntax.core.term.SortTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.MetaCall;
import org.aya.syntax.core.term.marker.Formation;
import org.aya.syntax.ref.LocalCtx;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.ref.MetaVar;
import org.aya.tyck.error.MetaVarError;
import org.aya.util.Decision;
import org.aya.util.Ordering;
import org.aya.util.Panic;
import org.aya.util.RelDec;
import org.aya.util.position.SourcePos;
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

  public @NotNull Unifier derive(@NotNull SourcePos pos, Ordering ordering) {
    return new Unifier(state, localCtx().derive(), reporter, pos, ordering, allowDelay);
  }

  /// @implNote about class meta: this function performs conversion check, and class metas can totally
  /// be solved just like a regular typed meta.
  @Override protected @Closed @NotNull RelDec<Term>
  doSolveMeta(@Closed @NotNull MetaCall meta, @Closed @NotNull Term rhs, @Closed @Nullable Term type) {
    // Assumption: rhs is in whnf
    var spine = meta.args();

    var inverted = MutableArrayList.<LocalVar>create(spine.size());
    var overlap = MutableList.<LocalVar>create();
    var wantToReturn = false;
    for (@Closed var arg : spine) {
      // TODO: apply uneta
      if (whnf(arg) instanceof FreeTerm(var var)) {
        if (inverted.contains(var)) overlap.append(var);
        inverted.append(var);
      } else if (allowVague) {
        inverted.append(LocalVar.generate("_"));
      } else if (allowDelay) {
        wantToReturn = true;
        break;
      } else if (!solveMetaInstances) {
        return RelDec.unsure();
      } else {
        reportBadSpine(meta, rhs);
        return RelDec.no();
      }
    }

    Term returnType;
    switch (computeReturnType(meta, rhs, type)) {
      case RelDec.Claim<Term> c -> {
        switch (c.downgrade()) {
          case NO -> {
            return RelDec.no();
          }
          case UNSURE -> {
            // computeReturnType does not return UNSURE
            return Panic.unreachable();
          }
          // fallthrough
          case YES -> {}
        }
        returnType = null;
      }
      case RelDec.Proof(var p) -> returnType = p;
    }
    if (wantToReturn) {
      state.addEqn(createEqn(meta, rhs, returnType));
      return RelDec.yes(returnType);
    }

    // In this case, the solution may not be unique (see #608),
    // so we may delay its resolution to the end of the tycking when we disallow delayed unification.
    var tmpRhs = rhs; // to get away with Java warning
    if (!allowVague && overlap.anyMatch(var -> FindUsage.free(tmpRhs, var) > 0)) {
      if (allowDelay) {
        state.addEqn(createEqn(meta, rhs, returnType));
        return RelDec.yes(returnType);
      } else if (!solveMetaInstances) {
        return RelDec.unsure();
      } else {
        reportBadSpine(meta, rhs);
        return RelDec.no();
      }
    }
    // Now we are sure that the variables in overlap are all unused.

    var findUsage = FindUsage.unfree(rhs, inverted);
    if (findUsage.termUsage > 0) {
      rhs = fullNormalize(rhs);
      findUsage = FindUsage.unfree(rhs, inverted);
    }
    if (findUsage.termUsage > 0) {
      fail(new MetaVarError.BadlyScopedError(meta, rhs, inverted));
      return RelDec.no();
    }
    if (findUsage.metaUsage > 0) {
      if (allowDelay) {
        state.addEqn(createEqn(meta, rhs, returnType));
        return RelDec.yes(returnType);
      } else {
        fail(new MetaVarError.BadlyScopedError(meta, rhs, inverted));
        return RelDec.no();
      }
    }
    var ref = meta.ref();
    if (FindUsage.meta(rhs, ref) > 0) {
      fail(new MetaVarError.RecursionError(meta, rhs));
      return RelDec.no();
    }
    var candidate = rhs.bindTele(inverted.view());
    // It might have extra arguments, in those cases we need to abstract them out.
    solve(ref, LamTerm.make(spine.size() - ref.ctxSize(), candidate));
    return RelDec.yes(returnType);
  }

  private @Closed @NotNull RelDec<Term>
  computeReturnType(@Closed @NotNull MetaCall meta, @Closed @NotNull Term rhs, @Closed @Nullable Term type) {
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
              return RelDec.no();
            }
          }
          default -> {
            var synthesize = checker.synthesizer().trySynth(rhs);
            if (!(synthesize instanceof SortTerm)) {
              reportIllTyped(meta, rhs);
              return RelDec.no();
            }
            if (returnType == null) returnType = synthesize;
          }
        }
        needUnify = false;
      }
      case MetaVar.OfType ofType -> {
        var target = ofType.type();
        var instTarget = MetaCall.appType(meta, target);
        if (type != null && compare(type, instTarget, null) != Decision.YES) {
          reportIllTyped(meta, rhs);
          return RelDec.no();
        }
        returnType = freezeHoles(instTarget);
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
    if (!needUnify && returnType == null) return RelDec.yes();
    return RelDec.of(returnType);
  }

  private void reportBadSpine(@NotNull MetaCall meta, @NotNull Term rhs) {
    fail(new MetaVarError.BadSpineError(meta, state, rhs));
  }
  private void reportIllTyped(@NotNull MetaCall meta, @NotNull Term rhs) {
    fail(new MetaVarError.IllTypedError(meta, state, rhs));
  }
}
