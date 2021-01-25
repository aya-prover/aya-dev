// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck.unify;

import org.glavo.kala.collection.Seq;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.core.term.AppTerm;
import org.mzi.core.term.LamTerm;
import org.mzi.core.term.RefTerm;
import org.mzi.core.term.Term;
import org.mzi.generic.Arg;
import org.mzi.ref.LocalVar;
import org.mzi.tyck.error.HoleBadSpineError;
import org.mzi.tyck.sort.LevelEqn;
import org.mzi.util.Ordering;

/**
 * The implementation of untyped pattern unification for holes.
 * András Kovács' elaboration-zoo is taken as reference.
 *
 * @author ice1000
 */
public class PatDefEq extends DefEq {
  public PatDefEq(@NotNull Ordering ord, LevelEqn.@NotNull Set equations) {
    super(ord, equations);
  }

  private @Nullable Tele extract(Seq<? extends Arg<? extends Term>> spine) {
    Tele buf = null;
    for (var arg : spine.view().reversed()) {
      if (arg.term() instanceof RefTerm ref && ref.var() instanceof LocalVar var) buf = new Tele.NamedTele(var, buf);
      else return null;
    }
    return buf;
  }

  @Override
  public @NotNull Boolean visitHole(AppTerm.@NotNull HoleApp lhs, @NotNull Term rhs, @Nullable Term type) {
    var patterns = extract(lhs.args());
    if (patterns == null) {
      equations.reporter().report(new HoleBadSpineError(lhs, expr));
      return false;
    }
    var solution = lhs.solution();
    if (solution.isDefined()) return compare(AppTerm.make(solution.get(), lhs.args()), rhs, type);
    // TODO[ice]: substitute these variables into new vars
    solution.set(new LamTerm(patterns, rhs));
    return true;
  }
}
