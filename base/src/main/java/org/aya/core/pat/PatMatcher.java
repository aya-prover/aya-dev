// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.pat;

import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.control.Result;
import kala.tuple.Tuple2;
import org.aya.core.def.PrimDef;
import org.aya.core.term.*;
import org.aya.core.visitor.Subst;
import org.aya.generic.Arg;
import org.aya.generic.util.InternalException;
import org.aya.tyck.env.LocalCtx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Matches a term with a pattern.
 *
 * @author ice1000
 * @apiNote Use {@link PatMatcher#tryBuildSubstArgs(PrimDef.Factory, LocalCtx, ImmutableSeq, SeqLike)} instead of instantiating the class directly.
 * @implNote The substitution built is made from parallel substitutions.
 */
public record PatMatcher(@NotNull Subst subst, @Nullable LocalCtx localCtx) {
  /**
   * @param localCtx not null only if we expect the presence of {@link RefTerm.MetaPat}
   * @return ok if the term matches the pattern,
   * err(false) if fails positively, err(true) if fails negatively
   */
  public static Result<Subst, Boolean> tryBuildSubstArgs(
    @NotNull PrimDef.Factory primFactory,
    @Nullable LocalCtx localCtx, @NotNull ImmutableSeq<@NotNull Pat> pats,
    @NotNull SeqLike<@NotNull Arg<@NotNull Term>> terms
  ) {
    return tryBuildSubstTerms(primFactory, localCtx, pats, terms.view().map(Arg::term));
  }

  /** @see PatMatcher#tryBuildSubstArgs(PrimDef.Factory, LocalCtx, ImmutableSeq, SeqLike) */
  public static Result<Subst, Boolean> tryBuildSubstTerms(
    @NotNull PrimDef.Factory primFactory,
    @Nullable LocalCtx localCtx, @NotNull ImmutableSeq<@NotNull Pat> pats,
    @NotNull SeqView<@NotNull Term> terms
  ) {
    var matchy = new PatMatcher(new Subst(new MutableHashMap<>()), localCtx);
    try {
      for (var pat : pats.zip(terms)) matchy.match(primFactory, pat);
      return Result.ok(matchy.subst());
    } catch (Mismatch mismatch) {
      return Result.err(mismatch.isBlocked);
    }
  }

  private void match(@NotNull PrimDef.Factory primFactory, @NotNull Pat pat, @NotNull Term term) throws Mismatch {
    switch (pat) {
      case Pat.Bind bind -> subst.addDirectly(bind.bind(), term);
      case Pat.Absurd ignored -> throw new InternalException("unreachable");
      case Pat.Ctor ctor -> {
        switch (term) {
          case CallTerm.Con conCall -> {
            if (ctor.ref() != conCall.ref()) throw new Mismatch(false);
            visitList(primFactory, ctor.params(), conCall.conArgs().view().map(Arg::term));
          }
          case RefTerm.MetaPat metaPat -> solve(pat, metaPat);
          default -> throw new Mismatch(true);
        }
      }
      case Pat.Tuple tuple -> {
        switch (term) {
          case IntroTerm.Tuple tup -> visitList(primFactory, tuple.pats(), tup.items());
          case RefTerm.MetaPat metaPat -> solve(pat, metaPat);
          default -> throw new Mismatch(true);
        }
      }
      case Pat.Meta meta -> {
        var sol = meta.solution().value;
        assert sol != null : "Unsolved pattern " + meta;
        match(primFactory, sol, term);
      }
      case Pat.Left left -> {
        if (term instanceof CallTerm.Right right) throw new Mismatch(true);
      }
      case Pat.Right right -> {
        if (term instanceof CallTerm.Left left) throw new Mismatch(true);
      }
    }
  }

  private void solve(@NotNull Pat pat, @NotNull RefTerm.MetaPat metaPat) throws Mismatch {
    var referee = metaPat.ref();
    var todo = referee.solution();
    if (todo.value != null) throw new UnsupportedOperationException(
      "unsure what to do, please file an issue with reproduction if you see this!");
    // In case this pattern matching is not from `PatTycker#mischa`, just block the evaluation.
    if (localCtx == null) throw new Mismatch(true);
    todo.value = pat.rename(subst, localCtx, referee.explicit());
  }

  private void visitList(@NotNull PrimDef.Factory primFactory, ImmutableSeq<Pat> lpats, SeqLike<Term> terms) throws Mismatch {
    assert lpats.sizeEquals(terms);
    lpats.view().zip(terms).forEachChecked(t -> match(primFactory, t));
  }

  private void match(@NotNull PrimDef.Factory primFactory, @NotNull Tuple2<Pat, Term> pp) throws Mismatch {
    match(primFactory, pp._1, pp._2);
  }

  private static final class Mismatch extends Exception {
    public final boolean isBlocked;

    private Mismatch(boolean isBlocked) {
      this.isBlocked = isBlocked;
    }
  }
}
