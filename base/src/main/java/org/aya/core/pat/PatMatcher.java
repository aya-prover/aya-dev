// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.pat;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.control.Result;
import org.aya.core.term.*;
import org.aya.core.visitor.EndoTerm;
import org.aya.core.visitor.Subst;
import org.aya.util.error.InternalException;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * Matches a term with a pattern.
 *
 * @author ice1000
 * @apiNote Use {@link PatMatcher#tryBuildSubst} instead of instantiating the class directly.
 * @implNote The substitution built is made from parallel substitutions.
 */
public record PatMatcher(@NotNull Subst subst, boolean inferMeta, @NotNull UnaryOperator<@NotNull Term> pre) {
  public static Result<Subst, Boolean> tryBuildSubst(
    boolean inferMeta, @NotNull ImmutableSeq<@NotNull Arg<@NotNull Pat>> pats,
    @NotNull ImmutableSeq<@NotNull Arg<@NotNull Term>> terms
  ) {
    return tryBuildSubst(inferMeta, pats, terms, UnaryOperator.identity());
  }

  /**
   * @param inferMeta true only if we expect the presence of {@link MetaPatTerm}
   * @return ok if the term matches the pattern,
   * err(false) if fails positively, err(true) if fails negatively
   */
  public static Result<Subst, Boolean> tryBuildSubst(
    boolean inferMeta, @NotNull ImmutableSeq<@NotNull Arg<@NotNull Pat>> pats,
    @NotNull ImmutableSeq<@NotNull Arg<@NotNull Term>> terms, @NotNull UnaryOperator<Term> pre
  ) {
    var matchy = new PatMatcher(new Subst(new MutableHashMap<>()), inferMeta, pre);
    try {
      pats.forEachWithChecked(terms, matchy::match);
      return Result.ok(matchy.subst());
    } catch (Mismatch mismatch) {
      return Result.err(mismatch.isBlocked);
    }
  }

  private void match(@NotNull Arg<Pat> pat, @NotNull Arg<Term> term) throws Mismatch {
    assert pat.explicit() == term.explicit() : "Explicitness mismatch";
    match(pat.term(), term.term());
  }

  private void match(@NotNull Pat pat, @NotNull Term term) throws Mismatch {
    switch (pat) {
      case Pat.Bind bind -> subst.addDirectly(bind.bind(), term);
      case Pat.Absurd ignored -> throw new InternalException("unreachable");
      case Pat.Ctor ctor -> {
        term = pre.apply(term);
        switch (term) {
          case ConCall conCall -> {
            if (ctor.ref() != conCall.ref()) throw new Mismatch(false);
            visitList(ctor.params(), conCall.conArgs());
          }
          case MetaPatTerm metaPat -> solve(pat, metaPat);
          // TODO[literal]: We may convert constructor call to literals to avoid possible stack overflow?
          case IntegerTerm litTerm -> match(ctor, litTerm.constructorForm());
          case ListTerm litTerm -> match(ctor, litTerm.constructorForm());
          case ShapedFnCall shapedConCall -> {
            if (ctor.ref() != shapedConCall.ref()) throw new Mismatch(false);
            visitList(ctor.params(), shapedConCall.args());
          }
          default -> throw new Mismatch(true);
        }
      }
      case Pat.Tuple tuple -> {
        term = pre.apply(term);
        switch (term) {
          case TupTerm tup -> visitList(tuple.pats(), tup.items());
          case MetaPatTerm metaPat -> solve(pat, metaPat);
          default -> throw new Mismatch(true);
        }
      }
      case Pat.Meta ignored -> throw new InternalException("Pat.Meta is not allowed");
      case Pat.ShapedInt lit -> {
        term = pre.apply(term);
        if (term instanceof IntegerTerm intTerm) {
          if (!lit.compareUntyped(intTerm)) throw new Mismatch(false);
        } else {
          throw new Mismatch(false);
        }
      }
    }
  }

  /**
   * We are matching some Pat against the MetaPat here:
   * <ul>
   *   <li>MetaPat is unresolved:
   *   replace all the bindings in Pat with a corresponding MetaPat (so they can be inferred),
   *   and then set it as the solution</li>
   *   <li>MetaPat is resolved: match the Pat and the solution of MetaPat</li>
   * </ul>
   *
   * @param pat make sure that pat is a "ctor" pat (such as Ctor, Tuple, Int, etc.) instead of a binding pat
   */
  private void solve(@NotNull Pat pat, @NotNull MetaPatTerm metaPat) throws Mismatch {
    var referee = metaPat.ref();
    var todo = referee.solution().get();

    // the MetaPat didn't solve
    if (todo == null) {
      // don't infer
      if (!inferMeta) throw new Mismatch(true);
      var bindSubst = new EndoTerm.MetaBind(subst, metaPat.ref().fakeBind().definition());
      var metalized = bindSubst.apply(pat);
      // solve as pat
      metaPat.ref().solution().set(metalized);
    } else {
      // a MetaPat that has solution <==> the solution
      match(pat, todo.toTerm());
    }
  }

  private void visitList(@NotNull ImmutableSeq<Arg<Pat>> lpats, @NotNull ImmutableSeq<Arg<Term>> terms) throws Mismatch {
    assert lpats.sizeEquals(terms) : "List size mismatch";
    lpats.forEachWithChecked(terms, this::match);
  }

  private static final class Mismatch extends Exception {
    public final boolean isBlocked;

    private Mismatch(boolean isBlocked) {
      this.isBlocked = isBlocked;
    }
  }
}
