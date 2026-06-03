// Copyright (c) 2020-2026 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.TermVisitor;
import org.aya.states.TyckState;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.DataDef;
import org.aya.syntax.core.def.DataDefLike;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.Callable;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.ref.LocalVar;
import org.aya.tyck.tycker.Problematic;
import org.aya.tyck.tycker.Stateful;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

/// A [CovarianceChecker] for inductive and inductive-recursive data types
///
/// Basically any [DefVar] in [InductiveChecker#mutual] can only appear in strictly positive position,
/// i.e. the codomain of the parameter of constructors. Positive but not strictly is rejected, i.e. `(A -> Empty) -> Empty`
///
/// Note that function may also appear in `mutual` as we have inductive-recursive, functions in `mutual` is also not allowed
/// to appear in non-strictly positive position.
///
/// TODO: in inductive-recursive, we may also check the body of function if it has anything like `Ind (Ind A)`
///
/// @param mutual a set of mutual definition that not yet typechecked
public record InductiveChecker(
  @NotNull ImmutableSeq<DefVar<?, ?>> mutual,
  @Override @NotNull TyckState state,
  @Override @NotNull Reporter reporter
  ) implements CovarianceChecker, Stateful, Problematic {
  public static void check(@NotNull ImmutableSeq<DefVar<?, ?>> recs, @NotNull TyckState state, @NotNull Reporter reporter) {
    var checker = new InductiveChecker(recs, state, reporter);

    for (var rec : recs) {
      if (rec.core == null) return;
      if (rec.core instanceof DataDef def) {
        var failed = checker.checkOne(def);
        if (failed) break;
      }
    }
  }

  private boolean checkOne(@NotNull DataDef def) {
    var body = def.body();

    // FIXME: how to deal with Path?
    for (var con : body) {
      try {
        con.selfTele.forEachIndexedChecked((idx, p) -> {
          var instP = p.type().instTele(FreeTerm.dummy(idx + con.ownerTele.size()).view());
          check(instP);
        });
      } catch (NonCovariantException e) {
        fail(new BadInduction(con.ref));
        return true;
      }
    }

    return false;
  }

  @Override
  public boolean isCovariance(@NotNull DataDefLike def, int at) {
    var var = AnyDef.toVar(def);

    // reject `| c : Ind (Ind A)` when checking `Ind`
    // well... this check may be not necessary, as [DataDef#covariance] is initialized with `false`s.
    if (mutual.contains(var)) {
      return false;
    }

    return def.isCovariant(at);
  }

  @Override
  public void checkNonCovariance(@Closed @NotNull Term term) throws NonCovariantException {
    var visitor = new CallVisitor();
    visitor.term(term);
    if (visitor.found) {
      throw new NonCovariantException();
    }
  }

  /// @see CallResolver.CallVisitor
  private class CallVisitor implements TermVisitor {
    boolean found = false;

    @Override
    public @NotNull Term term(@NotNull Term term) {
      if (found) return term;

      var whnf = whnf(term);
      if (whnf instanceof Callable.Tele call && mutual.contains(AnyDef.toVar(call.ref()))) {
        found = true;
        return term;
      }

      whnf.descent(this);
      return term;
    }
    @Override
    public @NotNull Closure closure(@NotNull Closure closure) {
      if (found) return closure;

      var applied = closure.apply(new LocalVar("_"));
      term(applied);
      return closure;
    }
  }
}
