// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.pat;

import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.control.Result;
import kala.tuple.Tuple2;
import org.aya.api.util.Arg;
import org.aya.core.def.PrimDef;
import org.aya.core.term.CallTerm;
import org.aya.core.term.IntroTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.Substituter;
import org.aya.tyck.LocalCtx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Matches a term with a pattern.
 *
 * @author ice1000
 * @apiNote Use {@link PatMatcher#tryBuildSubstArgs(LocalCtx, ImmutableSeq, SeqLike)} instead of instantiating the class directly.
 * @implNote The substitution built is made from parallel substitutions.
 */
public record PatMatcher(@NotNull Substituter.TermSubst subst, @Nullable LocalCtx localCtx) {
  /**
   * @param localCtx not null only if we expect the presence of {@link RefTerm.MetaPat}
   * @return ok if the term matches the pattern,
   * err(false) if fails positively, err(true) if fails negatively
   */
  public static Result<Substituter.TermSubst, Boolean> tryBuildSubstArgs(
    @Nullable LocalCtx localCtx, @NotNull ImmutableSeq<@NotNull Pat> pats,
    @NotNull SeqLike<@NotNull Arg<@NotNull Term>> terms
  ) {
    return tryBuildSubstTerms(localCtx, pats, terms.view().map(Arg::term));
  }

  /** @see PatMatcher#tryBuildSubstArgs(LocalCtx, ImmutableSeq, SeqLike) */
  public static Result<Substituter.TermSubst, Boolean> tryBuildSubstTerms(
    @Nullable LocalCtx localCtx, @NotNull ImmutableSeq<@NotNull Pat> pats,
    @NotNull SeqView<@NotNull Term> terms
  ) {
    var matchy = new PatMatcher(new Substituter.TermSubst(new MutableHashMap<>()), localCtx);
    try {
      for (var pat : pats.zip(terms)) matchy.match(pat);
      return Result.ok(matchy.subst());
    } catch (Mismatch mismatch) {
      return Result.err(mismatch.isBlocked);
    }
  }

  private void match(@NotNull Pat pat, @NotNull Term term) throws Mismatch {
    switch (pat) {
      case Pat.Bind bind -> subst.addDirectly(bind.as(), term);
      case Pat.Absurd absurd -> throw new IllegalStateException("unreachable");
      case Pat.Prim prim -> {
        var core = prim.ref().core;
        assert PrimDef.Factory.INSTANCE.leftOrRight(core);
        switch (term) {
          case CallTerm.Prim primCall -> {
            if (primCall.ref() != prim.ref()) throw new Mismatch(false);
          }
          case RefTerm.MetaPat metaPat -> solve(pat, metaPat);
          default -> throw new Mismatch(true);
        }
      }
      case Pat.Ctor ctor -> {
        switch (term) {
          case CallTerm.Con conCall -> {
            var as = ctor.as();
            if (as != null) subst.addDirectly(as, conCall);
            if (ctor.ref() != conCall.ref()) throw new Mismatch(false);
            visitList(ctor.params(), conCall.conArgs().view().map(Arg::term));
          }
          case RefTerm.MetaPat metaPat -> solve(pat, metaPat);
          default -> throw new Mismatch(true);
        }
      }
      case Pat.Tuple tuple -> {
        switch (term) {
          case IntroTerm.Tuple tup -> {
            var as = tuple.as();
            if (as != null) subst.addDirectly(as, tup);
            visitList(tuple.pats(), tup.items());
          }
          case RefTerm.MetaPat metaPat -> solve(pat, metaPat);
          default -> throw new Mismatch(true);
        }
      }
      case Pat.Meta meta -> {
        var sol = meta.solution().value;
        assert sol != null : "Unsolved pattern " + meta;
        match(sol, term);
      }
    }
  }

  private void solve(@NotNull Pat pat, @NotNull RefTerm.MetaPat metaPat) {
    var referee = metaPat.ref();
    var todo = referee.solution();
    if (todo.value != null) throw new UnsupportedOperationException(
      "unsure what to do, please file an issue with reproduction if you see this!");
    assert localCtx != null;
    todo.value = pat.rename(subst, localCtx, referee.explicit());
  }

  private void visitList(ImmutableSeq<Pat> lpats, SeqLike<Term> terms) throws Mismatch {
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
