// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.pat;

import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.control.Result;
import kala.tuple.Tuple2;
import org.aya.core.term.*;
import org.aya.core.visitor.PatTraversal;
import org.aya.core.visitor.Subst;
import org.aya.generic.Arg;
import org.aya.generic.util.InternalException;
import org.aya.guest0x0.cubical.Formula;
import org.aya.tyck.env.LocalCtx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

/**
 * Matches a term with a pattern.
 *
 * @author ice1000
 * @apiNote Use {@link PatMatcher#tryBuildSubstTerms} instead of instantiating the class directly.
 * @implNote The substitution built is made from parallel substitutions.
 *
 * FIXME[hoshino]: localCtx is useless now, it can be replaced with a {@code (inferable : Boolean)}
 */
public record PatMatcher(@NotNull Subst subst, @Nullable LocalCtx localCtx, @NotNull UnaryOperator<@NotNull Term> pre) {
  public static Result<Subst, Boolean> tryBuildSubstTerms(
    @Nullable LocalCtx localCtx, @NotNull ImmutableSeq<@NotNull Pat> pats,
    @NotNull SeqView<@NotNull Term> terms
  ) {
    return tryBuildSubstTerms(localCtx, pats, terms, UnaryOperator.identity());
  }

  /**
   * @param localCtx not null only if we expect the presence of {@link MetaPatTerm}
   * @return ok if the term matches the pattern,
   * err(false) if fails positively, err(true) if fails negatively
   */
  public static Result<Subst, Boolean> tryBuildSubstTerms(
    @Nullable LocalCtx localCtx, @NotNull ImmutableSeq<@NotNull Pat> pats,
    @NotNull SeqView<@NotNull Term> terms, @NotNull UnaryOperator<Term> pre
  ) {
    var matchy = new PatMatcher(new Subst(new MutableHashMap<>()), localCtx, pre);
    try {
      for (var pat : pats.zip(terms)) matchy.match(pat);
      return Result.ok(matchy.subst());
    } catch (Mismatch mismatch) {
      return Result.err(mismatch.isBlocked);
    }
  }

  private void match(@NotNull Pat pat, @NotNull Term term) throws Mismatch {
    switch (pat) {
      case Pat.Bind bind -> subst.addDirectly(bind.bind(), term);
      case Pat.Absurd ignored -> throw new InternalException("unreachable");
      case Pat.Ctor ctor -> {
        term = pre.apply(term);
        switch (term) {
          case CallTerm.Con conCall -> {
            if (ctor.ref() != conCall.ref()) throw new Mismatch(false);
            visitList(ctor.params(), conCall.conArgs().view().map(Arg::term));
          }
          case MetaPatTerm metaPat -> solve(pat, metaPat);
          // TODO[literal]: We may convert constructor call to literals to avoid possible stack overflow?
          case IntegerTerm litTerm -> match(ctor, litTerm.constructorForm());
          case ListTerm litTerm -> match(ctor, litTerm.constructorForm());
          default -> throw new Mismatch(true);
        }
      }
      case Pat.Tuple tuple -> {
        term = pre.apply(term);
        switch (term) {
          case IntroTerm.Tuple tup -> visitList(tuple.pats(), tup.items());
          case MetaPatTerm metaPat -> solve(pat, metaPat);
          default -> throw new Mismatch(true);
        }
      }
      case Pat.Meta ignored -> throw new InternalException("Pat.Meta is not allowed");
      case Pat.End end -> {
        term = pre.apply(term);
        if (!(term.asFormula() instanceof Formula.Lit<Term> termEnd && termEnd.isOne() == end.isOne())) {
          throw new Mismatch(true);
        }
      }
      case Pat.ShapedInt lit -> {
        term = pre.apply(term);
        switch (term) {
          case IntegerTerm litTerm -> {
            if (!lit.compareUntyped(litTerm)) throw new Mismatch(false);
          }
          // TODO[literal]: We may convert constructor call to literals to avoid possible stack overflow?
          case CallTerm.Con con -> match(lit.constructorForm(), con);
          // we only need to handle matching both literals, otherwise we just rematch it
          // with constructor form to reuse the code as much as possible (like solving MetaPats).
          default -> match(lit.constructorForm(), term);
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
      if (localCtx == null) throw new Mismatch(true);
      var bindSubst = new PatTraversal.MetaBind(this.subst, metaPat.ref().fakeBind().definition());
      var metalized = bindSubst.apply(pat);
      // solve as pat
      metaPat.ref().solution().set(metalized);
    } else {
      // a MetaPat that has solution <==> the solution
      match(pat, todo.toTerm());
    }
  }

  private void visitList(@NotNull ImmutableSeq<Pat> lpats, @NotNull SeqLike<Term> terms) throws Mismatch {
    assert lpats.sizeEquals(terms);
    lpats.view().zip(terms).forEachChecked(this::match);
  }

  private void match(@NotNull Tuple2<Pat, Term> pp) throws Mismatch {
    match(pp._1, pp._2);
  }

  private static final class Mismatch extends Exception {
    public final boolean isBlocked;

    private Mismatch(boolean isBlocked) {
      this.isBlocked = isBlocked;
    }
  }
}
