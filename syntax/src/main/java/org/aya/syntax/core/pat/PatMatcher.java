// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.pat;

import java.util.function.UnaryOperator;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.FreezableMutableList;
import kala.collection.mutable.MutableList;
import kala.control.Result;
import org.aya.generic.State;
import org.aya.syntax.core.term.MetaPatTerm;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

/**
 *
 */
public final class PatMatcher extends MatcherBase {
  private final boolean inferMeta;
  private final @NotNull FreezableMutableList<Term> matched = FreezableMutableList.create();

  /**
   * @param inferMeta whether infer the PatMetaTerm
   */
  public PatMatcher(boolean inferMeta, @NotNull UnaryOperator<Term> pre) {
    super(pre);
    this.inferMeta = inferMeta;
  }

  @Override protected void onMatchBind(Pat.Bind bind, @NotNull Term matched) {
    onMatchBind(matched);
  }
  private void onMatchBind(@NotNull Term matched) { this.matched.append(matched); }

  /// @return a substitution of corresponding bindings of {@param pats} if success.
  /// @apiNote The binding order is the same as [#collectVariables]
  /// @see State
  public @NotNull Result<ImmutableSeq<Term>, State> apply(
    @NotNull ImmutableSeq<Pat> pats,
    @NotNull ImmutableSeq<Term> terms
  ) {
    try {
      matchMany(pats, terms);
      return Result.ok(matched.toImmutableSeq());
    } catch (MatcherBase.Failure e) {
      return Result.err(e.reason);
    }
  }

  public @NotNull Result<Term, State> apply(
    @NotNull Term.Matching matching,
    @NotNull ImmutableSeq<Term> terms
  ) {
    try {
      matchMany(matching.patterns(), terms);
      return Result.ok(matching.body().instTele(matched.freeze().view()));
    } catch (MatcherBase.Failure e) {
      return Result.err(e.reason);
    }
  }

  @Override protected void onMetaPat(@NotNull Pat pat, @NotNull MetaPatTerm term) throws MatcherBase.Failure {
    var maybeMeta = realSolution(term);
    if (maybeMeta instanceof MetaPatTerm(var meta)) {
      if (inferMeta) {
        var bindsMetas = doSolveMeta(pat, meta);
        bindsMetas.forEach(this::onMatchBind);
      } else throw new MatcherBase.Failure(State.Stuck);
    } else {
      match(pat, maybeMeta);
    }
  }

  private static @NotNull Term realSolution(@NotNull MetaPatTerm term) {
    Pat pat = term.meta();
    while (pat instanceof Pat.Meta meta && meta.solution().get() instanceof Pat notNullPat) pat = notNullPat;
    return PatToTerm.visit(pat);
  }

  public @NotNull ImmutableSeq<Term> doSolveMeta(@NotNull Pat pat, Pat.Meta meta) {
    assert meta.solution().get() == null;
    // No solution, set the current pattern as solution,
    // also replace the bindings in pat as sub-meta,
    // so that we can solve this meta more.

    var eater = new BindEater(matched.toImmutableSeq(), MutableList.create());
    var boroboroPat = eater.apply(pat);   // It looks boroboro, there are holes on it.
    meta.solution().set(boroboroPat);

    return eater.mouth().toImmutableSeq();
  }
}
