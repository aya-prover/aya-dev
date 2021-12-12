// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.pat;

import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.control.Either;
import kala.control.Result;
import org.aya.api.util.Arg;
import org.aya.core.def.PrimDef;
import org.aya.core.term.CallTerm;
import org.aya.core.term.IntroTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.Substituter;
import org.aya.tyck.env.LocalCtx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Matches a term with a pattern/lhs.
 * When matching a lhs, there should be no {@link RefTerm.MetaPat}, which means localCtx shhould be null
 *
 * @author ice1000
 * @apiNote Use {@link LhsPatMatcher#tryBuildLhsSubstArgs(ImmutableSeq, SeqLike)} instead of instantiating the class directly.
 * @param localCtx not null only if we expect the presence of {@link RefTerm.MetaPat}
 * @implNote The substitution built is made from parallel substitutions.
 */
public record LhsPatMatcher(@NotNull Substituter.TermSubst subst, @Nullable LocalCtx localCtx) {

  /**
   * @return ok if the term matches the pattern,
   * err(false) if fails positively, err(true) if fails negatively
   */
  public static Result<Substituter.TermSubst, Boolean> tryBuildLhsSubstArgs(
    @NotNull ImmutableSeq<@NotNull Lhs> lhss,
    @NotNull SeqLike<@NotNull Arg<@NotNull Term>> terms
  ) {
    return tryBuildLhsSubstTerms(lhss, terms.view().map(Arg::term));
  }

  public static Result<Substituter.TermSubst, Boolean> tryBuildPatSubstArgs(
    @NotNull LocalCtx localCtx, @NotNull ImmutableSeq<@NotNull Pat> pats,
    @NotNull SeqView<@NotNull Arg<@NotNull Term>> terms
  ) {
    return tryBuildPatSubstTerms(localCtx, pats, terms.map(Arg::term));
  }

  public static Result<Substituter.TermSubst, Boolean> tryBuildPatSubstTerms(
    @NotNull LocalCtx localCtx, @NotNull ImmutableSeq<@NotNull Pat> pats,
    @NotNull SeqView<@NotNull Term> terms
  ) {
    var matchy = new LhsPatMatcher(new Substituter.TermSubst(new MutableHashMap<>()), localCtx);
    try {
      for (var pat : pats.map(Either::<Lhs, Pat>right).zip(terms)) matchy.match(pat._1, pat._2);
      return Result.ok(matchy.subst());
    } catch (Mismatch mismatch) {
      return Result.err(mismatch.isBlocked);
    }
  }

  /** @see LhsPatMatcher#tryBuildLhsSubstArgs(ImmutableSeq, SeqLike) */
  public static Result<Substituter.TermSubst, Boolean> tryBuildLhsSubstTerms(
    @NotNull ImmutableSeq<@NotNull Lhs> lhss,
    @NotNull SeqView<@NotNull Term> terms
  ) {
    var matchy = new LhsPatMatcher(new Substituter.TermSubst(new MutableHashMap<>()), null);
    try {
      for (var lhs : lhss.map(Either::<Lhs, Pat>left).zip(terms)) matchy.match(lhs._1, lhs._2);
      return Result.ok(matchy.subst());
    } catch (Mismatch mismatch) {
      return Result.err(mismatch.isBlocked);
    }
  }

  private void matchCore(@NotNull Either<Lhs, Pat> lhsOrPat, @NotNull Term term) throws Mismatch {
    // If we want to match a pattern with a MetaPat, then we just solve it and return
    if (lhsOrPat.isRight() && !(lhsOrPat.getRightValue() instanceof Pat.Bind) &&
      term instanceof RefTerm.MetaPat metaPat) {
      solve(lhsOrPat.getRightValue(), metaPat);
      return ;
    }
    // Now there are no MetaPat, so we can got Lhs from Pat and just perform the matching
    Lhs lhs = lhsOrPat.isRight() ? lhsOrPat.getRightValue().toLhs() : lhsOrPat.getLeftValue();
    switch (lhs) {
      case Lhs.Bind bind -> subst.addDirectly(bind.bind(), term);
      case Lhs.Prim prim -> {
        var core = prim.ref().core;
        assert PrimDef.Factory.INSTANCE.leftOrRight(core);
        switch (term) {
          case CallTerm.Prim primCall -> {
            if (primCall.ref() != prim.ref()) throw new Mismatch(false);
          }
          default -> throw new Mismatch(true);
        }
      }
      case Lhs.Ctor ctor -> {
        switch (term) {
          case CallTerm.Con conCall -> {
            if (ctor.ref() != conCall.ref()) throw new Mismatch(false);
            visitList(ctor.params(), conCall.conArgs().view().map(Arg::term));
          }
          default -> throw new Mismatch(true);
        }
      }
      case Lhs.Tuple tuple -> {
        switch (term) {
          case IntroTerm.Tuple tup -> visitList(tuple.lhss(), tup.items());
          default -> throw new Mismatch(true);
        }
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

  private void visitList(ImmutableSeq<Lhs> lhss, SeqLike<Term> terms) throws Mismatch {
    assert lhss.sizeEquals(terms);
    lhss.map(Either::<Lhs, Pat>left).zip(terms).forEachChecked((lpt) -> match(lpt._1, lpt._2));
  }

  private void match(@NotNull Either<Lhs, Pat> lhsOrPat, @NotNull Term term) throws Mismatch {
    matchCore(lhsOrPat, term);
  }

  private static final class Mismatch extends Exception {
    public final boolean isBlocked;

    private Mismatch(boolean isBlocked) {
      this.isBlocked = isBlocked;
    }
  }
}
