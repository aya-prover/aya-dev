// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.unify;

import kala.collection.mutable.MutableHashMap;
import org.aya.core.Meta;
import org.aya.core.ops.Eta;
import org.aya.core.term.*;
import org.aya.core.visitor.DeltaExpander;
import org.aya.core.visitor.Subst;
import org.aya.generic.util.InternalException;
import org.aya.generic.util.NormalizeMode;
import org.aya.tyck.TyckState;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.env.MapLocalCtx;
import org.aya.tyck.error.HoleProblem;
import org.aya.tyck.trace.Trace;
import org.aya.util.Ordering;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class implements pattern unification with delayed constraints solving.
 * It is extracted from {@link TermComparator} for modularity and readability.
 *
 * @implNote in case {@link Unifier#compareUntyped(Term, Term, Sub, Sub)} returns null,
 * we will consider it a unification failure, so be careful when returning null.
 * @see Eta Eta-contraction
 * @see TermComparator bidirectional conversion check
 * @see Unifier#compare(Term, Term, Term) the only intended API for conversion checking
 */
public final class Unifier extends TermComparator {
  final boolean allowVague;
  final boolean allowConfused;
  private final @NotNull Eta uneta;

  public Unifier(
    @NotNull Ordering cmp, @NotNull Reporter reporter,
    boolean allowVague, boolean allowConfused,
    @Nullable Trace.Builder traceBuilder, @NotNull TyckState state,
    @NotNull SourcePos pos, @NotNull LocalCtx ctx
  ) {
    super(traceBuilder, state, reporter, pos, cmp, ctx);
    this.allowVague = allowVague;
    this.allowConfused = allowConfused;
    uneta = new Eta(ctx);
  }

  private @NotNull TyckState.Eqn createEqn(@NotNull Term lhs, @NotNull Term rhs, Sub lr, Sub rl) {
    var local = new MapLocalCtx();
    ctx.forward(local, lhs, state);
    ctx.forward(local, rhs, state);
    return new TyckState.Eqn(lhs, rhs, cmp, pos, local, lr.clone(), rl.clone());
  }

  private @Nullable Subst extract(
    @NotNull CallTerm.Hole lhs, @NotNull Term rhs, @NotNull Meta meta
  ) {
    var subst = new Subst(new MutableHashMap<>(/*spine.size() * 2*/));
    for (var arg : lhs.args().view().zip(meta.telescope)) {
      if (uneta.uneta(arg._1.term()) instanceof RefTerm ref) {
        if (subst.map().containsKey(ref.var())) return null;
        subst.add(ref.var(), arg._2.toTerm());
      } else return null;
    }
    return subst;
  }

  @Override @Nullable protected Term solveMeta(@NotNull Term preRhs, Sub lr, Sub rl, CallTerm.@NotNull Hole lhs) {
    var meta = lhs.ref();
    if (preRhs instanceof CallTerm.Hole rcall && lhs.ref() == rcall.ref()) {
      // If we do not know the type, then we do not perform the comparison
      if (meta.result == null) return null;
      // Is this going to produce a readable error message?
      compareSort(new FormTerm.Type(lhs.ulift()), new FormTerm.Type(rcall.ulift()));
      var holeTy = FormTerm.Pi.make(meta.telescope, meta.result);
      for (var arg : lhs.args().view().zip(rcall.args())) {
        if (!(holeTy instanceof FormTerm.Pi holePi))
          throw new InternalException("meta arg size larger than param size. this should not happen");
        if (!compare(arg._1.term(), arg._2.term(), lr, rl, holePi.param().type())) return null;
        holeTy = holePi.substBody(arg._1.term());
      }
      return holeTy.lift(lhs.ulift());
    }
    // Long time ago I wrote this to generate more unification equations,
    // which solves more universe levels. However, with latest version Aya (0.13),
    // removing this does not break anything.
    // Update: this is still needed, see #327 last task (`coe'`)
    var resultTy = preRhs.computeType(state, ctx);
    // resultTy might be an ErrorTerm, what to do?
    if (meta.result != null) {
      compareUntyped(resultTy, meta.result.lift(lhs.ulift()), rl, lr);
    }
    var argSubst = extract(lhs, preRhs, meta);
    if (argSubst == null) {
      reporter.report(new HoleProblem.BadSpineError(lhs, pos));
      return null;
    }
    var subst = DeltaExpander.buildSubst(meta.contextTele, lhs.contextArgs());
    // In this case, the solution may not be unique (see #608),
    // so we may delay its resolution to the end of the tycking when we disallow vague unification.
    if (!allowVague && subst.overlap(argSubst).anyMatch(var -> preRhs.findUsages(var) > 0)) {
      state.addEqn(createEqn(lhs, preRhs, lr, rl));
      // Skip the unification and scope check
      return resultTy;
    }
    subst.add(argSubst);
    // TODO
    // TODO: what's the TODO above? I don't know what's TODO? ????
    rl.map().forEach(subst::add);
    assert !state.metas().containsKey(meta);
    var solved = preRhs.freezeHoles(state).subst(subst, -lhs.ulift());
    var allowedVars = meta.fullTelescope().map(Term.Param::ref).toImmutableSeq();
    var scopeCheck = solved.scopeCheck(allowedVars);
    if (scopeCheck.invalid.isNotEmpty()) {
      // Normalization may remove the usages of certain variables
      solved = solved.normalize(state, NormalizeMode.NF);
      scopeCheck = solved.scopeCheck(allowedVars);
    }
    if (scopeCheck.invalid.isNotEmpty()) {
      reporter.report(new HoleProblem.BadlyScopedError(lhs, solved, scopeCheck.invalid, pos));
      return new ErrorTerm(solved);
    }
    if (scopeCheck.confused.isNotEmpty()) {
      if (allowConfused) state.addEqn(createEqn(lhs, solved, lr, rl));
      else {
        reporter.report(new HoleProblem.BadlyScopedError(lhs, solved, scopeCheck.confused, pos));
        return new ErrorTerm(solved);
      }
    }
    if (!meta.solve(state, solved)) {
      reporter.report(new HoleProblem.RecursionError(lhs, solved, pos));
      return new ErrorTerm(solved);
    }
    tracing(builder -> builder.append(new Trace.LabelT(pos, "Hole solved!")));
    return resultTy;
  }

  public void checkEqn(@NotNull TyckState.Eqn eqn) {
    compareUntyped(
      eqn.lhs().normalize(state, NormalizeMode.WHNF),
      eqn.rhs().normalize(state, NormalizeMode.WHNF),
      eqn.lr(), eqn.rl()
    );
  }
}
