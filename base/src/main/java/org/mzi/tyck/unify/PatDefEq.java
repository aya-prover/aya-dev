// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.tyck.unify;

import org.glavo.kala.collection.Seq;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.core.term.AppTerm;
import org.mzi.core.term.LamTerm;
import org.mzi.core.term.RefTerm;
import org.mzi.core.term.Term;
import org.mzi.core.visitor.Substituter;
import org.mzi.generic.Arg;
import org.mzi.ref.LocalVar;
import org.mzi.tyck.MetaContext;
import org.mzi.tyck.error.HoleBadSpineWarn;
import org.mzi.util.Ordering;

import java.util.HashMap;

/**
 * The implementation of untyped pattern unification for holes.
 * András Kovács' elaboration-zoo is taken as reference.
 *
 * @author ice1000
 */
public class PatDefEq extends DefEq {
  public PatDefEq(@NotNull Ordering ord, @NotNull MetaContext metaContext) {
    super(ord, metaContext);
  }

  private @Nullable Term extract(Seq<? extends Arg<? extends Term>> spine, Term rhs) {
    var subst = new Substituter.TermSubst(new HashMap<>(spine.size() * 2));
    for (var arg : spine.view()) {
      if (arg.term() instanceof RefTerm ref && ref.var() instanceof LocalVar var) {
        rhs = extractVar(rhs, subst, arg, var);
        if (rhs == null) return null;
      } else return null;
      // TODO[ice]: ^ eta var
    }
    return rhs.subst(subst);
  }

  private @Nullable Term extractVar(Term rhs, Substituter.TermSubst subst, Arg<? extends Term> arg, LocalVar var) {
    if (subst.map().containsKey(var)) {
      // TODO[ice]: report errors for duplicated vars in spine
      return null;
    }
    var type = new AppTerm.HoleApp(new LocalVar("_"));
    var abstracted = new LocalVar(var.name() + "'");
    var param = new Term.Param(abstracted, type, arg.explicit());
    subst.add(var, new RefTerm(abstracted));
    return new LamTerm(param, new LamTerm(param, rhs));
  }

  @Override
  public @NotNull Boolean visitHole(AppTerm.@NotNull HoleApp lhs, @NotNull Term rhs, @Nullable Term type) {
    var solved = extract(lhs.args(), rhs);
    if (solved == null) {
      metaContext.report(new HoleBadSpineWarn(lhs, expr));
      return false;
    }
    var solution = metaContext.solutions().getOption(lhs);
    if (solution.isDefined()) return compare(AppTerm.make(solution.get(), lhs.args()), rhs, type);
    metaContext.solutions().put(lhs, solved);
    return true;
  }
}
