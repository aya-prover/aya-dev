// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.unify;

import kala.collection.Seq;
import kala.collection.mutable.MutableArrayList;
import kala.control.Option;
import org.aya.core.Meta;
import org.aya.core.ops.Eta;
import org.aya.core.term.*;
import org.aya.core.visitor.DeltaExpander;
import org.aya.core.visitor.Subst;
import org.aya.generic.util.InternalException;
import org.aya.generic.util.NormalizeMode;
import org.aya.ref.LocalVar;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.env.MapLocalCtx;
import org.aya.tyck.error.HoleProblem;
import org.aya.tyck.trace.Trace;
import org.aya.tyck.tycker.TyckState;
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
 * @see #compare(Term, Term, Term) the only intended API for conversion checking
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

  /**
   * @param subst is added with unique variables in the inverted spine
   * @return the list of duplicated variables if the spine is successfully inverted.
   */
  private @Nullable Seq<LocalVar> invertSpine(Subst subst, @NotNull MetaTerm lhs, @NotNull Meta meta) {
    var overlap = MutableArrayList.<LocalVar>create();
    for (var arg : lhs.args().zipView(meta.telescope)) {
      if (uneta.uneta(arg._1.term()) instanceof RefTerm ref) {
        if (overlap.contains(ref.var())) continue;
        if (subst.map().containsKey(ref.var())) {
          overlap.append(ref.var());
          subst.map().remove(ref.var());
        }
        subst.add(ref.var(), arg._2.toTerm());
      } else return null;
    }
    return overlap;
  }

  @Override @Nullable protected Term solveMeta(@NotNull Term preRhs, Sub lr, Sub rl, @NotNull MetaTerm lhs, @Nullable Term providedType) {
    var meta = lhs.ref();
    var sameMeta = sameMeta(lr, rl, lhs, meta, preRhs);
    if (sameMeta.isDefined()) return sameMeta.get();
    // Long time ago I wrote this to generate more unification equations,
    // which solves more universe levels. However, with latest version Aya (0.13),
    // removing this does not break anything.
    // Update: this is still needed, see #327 last task (`coe'`)
    var checker = new DoubleChecker(new Unifier(Ordering.Lt,
      reporter, false, false, traceBuilder, state, pos, ctx.deriveMap()), lr, rl);
    var expectedType = meta.result;
    if (expectedType != null) {
      // resultTy might be an ErrorTerm, what to do?
      if (!checker.inherit(preRhs, expectedType))
        reporter.report(new HoleProblem.IllTypedError(lhs, preRhs));
    } else {
      expectedType = checker.synthesizer().synthesize(preRhs);
      if (expectedType == null) {
        throw new UnsupportedOperationException("TODO: add an error report for this");
      }
    }
    // Pattern unification: buildSubst(lhs.args.invert(), meta.telescope)
    var subst = DeltaExpander.buildSubst(meta.contextTele, lhs.contextArgs());
    var overlap = invertSpine(subst, lhs, meta);
    if (overlap == null) {
      reporter.report(new HoleProblem.BadSpineError(lhs));
      return null;
    }
    // In this case, the solution may not be unique (see #608),
    // so we may delay its resolution to the end of the tycking when we disallow vague unification.
    if (!allowVague && overlap.anyMatch(var -> preRhs.findUsages(var) > 0)) {
      state.addEqn(createEqn(lhs, preRhs, lr, rl));
      // Skip the unification and scope check
      return expectedType;
    }
    // Now we are sure that the variables in overlap are all unused.

    // The substitution `rl` maps the intermediate, fresh names to the LHS of unification.
    // We use intermediate fresh names as suggested by Andras Kovacs
    // Now, two things can make use of those intermediate fresh names:
    //  1. lhs.args(), 2. preRhs
    // The generated substitution is okay, because if it has the fresh names,
    //  they will be replaced with meta.telescope variables which is well-scoped.
    // However, if preRhs refer to a fresh name, we need to substitute it to the LHS
    //  version of them. Hence, we use rl to modify `subst`.
    // According to the above rationales, this should not be replaced with `subst.map.putAll(rl.map)`.
    rl.map().forEach(subst::add);
    // Since we're here, this is definitely an unsolved meta. Assert that just in case
    //  we break the logic.
    assert !state.metas().containsKey(meta);
    var solved = preRhs.freezeHoles(state).subst(subst);
    sameMeta = sameMeta(lr, rl, lhs, meta, solved);
    if (sameMeta.isDefined()) return sameMeta.get();
    var allowedVars = meta.fullTelescope().map(Term.Param::ref).toImmutableSeq();
    // First, try to scope check without normalization
    var scopeCheck = solved.scopeCheck(allowedVars);
    if (scopeCheck.invalid.isNotEmpty()) {
      // Normalization may remove the usages of certain variables
      solved = solved.normalize(state, NormalizeMode.NF);
      scopeCheck = solved.scopeCheck(allowedVars);
    }
    if (scopeCheck.invalid.isNotEmpty()) {
      reporter.report(new HoleProblem.BadlyScopedError(lhs, solved, scopeCheck.invalid));
      return new ErrorTerm(solved);
    }
    if (scopeCheck.confused.isNotEmpty()) {
      if (allowConfused) state.addEqn(createEqn(lhs, solved, lr, rl));
      else {
        reporter.report(new HoleProblem.BadlyScopedError(lhs, solved, scopeCheck.confused));
        return new ErrorTerm(solved);
      }
    }
    if (!state.solve(meta, solved)) {
      reporter.report(new HoleProblem.RecursionError(lhs, solved));
      return new ErrorTerm(solved);
    }
    tracing(builder -> builder.append(new Trace.LabelT(pos, "Hole solved!")));
    return expectedType;
  }

  /**
   * @return none if not the same meta, some(null) if return null directly
   */
  private @NotNull Option<@Nullable Term> sameMeta(Sub lr, Sub rl, @NotNull MetaTerm lhs, Meta meta, Term preRhs) {
    if (!(preRhs instanceof MetaTerm rcall && meta == rcall.ref())) return Option.none();
    // If we do not know the type, then we do not perform the comparison
    if (meta.result == null) return Option.some(null);
    var holeTy = PiTerm.make(meta.telescope, meta.result);
    for (var arg : lhs.args().zipView(rcall.args())) {
      if (!(holeTy instanceof PiTerm holePi))
        throw new InternalException("meta arg size larger than param size. this should not happen");
      if (!compare(arg._1.term(), arg._2.term(), lr, rl, holePi.param().type()))
        return Option.some(null);
      holeTy = holePi.substBody(arg._1.term());
    }
    return Option.some(holeTy);
  }

  public void checkEqn(@NotNull TyckState.Eqn eqn) {
    compareUntyped(
      eqn.lhs().normalize(state, NormalizeMode.WHNF),
      eqn.rhs().normalize(state, NormalizeMode.WHNF),
      eqn.lr(), eqn.rl()
    );
  }
}
