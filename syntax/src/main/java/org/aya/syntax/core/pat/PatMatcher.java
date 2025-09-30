// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.pat;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.FreezableMutableList;
import kala.collection.mutable.MutableList;
import kala.control.Result;
import org.aya.generic.State;
import org.aya.syntax.core.term.MetaPatTerm;
import org.aya.syntax.core.term.Term;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 *
 */
public abstract class PatMatcher extends MatcherBase {
  protected final @NotNull FreezableMutableList<Term> matched = FreezableMutableList.create();

  public PatMatcher(@NotNull UnaryOperator<Term> pre) { super(pre); }
  @Override protected void onMatchBind(Pat.Bind bind, @NotNull Term matched) { onMatchBind(matched); }
  protected void onMatchBind(@NotNull Term matched) { this.matched.append(matched); }

  /// @return a substitution of corresponding bindings of {@param pats} if success.
  /// @apiNote The binding order is the same as [Pat#collectVariables]
  /// @see State
  public @NotNull Result<ImmutableSeq<Term>, State> apply(
    @NotNull ImmutableSeq<Pat> pats,
    @NotNull ImmutableSeq<Term> terms
  ) {
    try {
      matchMany(pats, terms);
      return Result.ok(matched.toSeq());
    } catch (MatcherBase.Failure e) {
      return Result.err(e.reason);
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

    // this is why no scope problem, we don't use those foreign [Pat.Bind] directly, instead, we replace them with [Pat.Meta],
    // and make fresh [LocalVar] for them when inlined.
    var eater = new BindEater(matched.toSeq(), MutableList.create());
    var boroboroPat = eater.apply(pat);   // It looks boroboro, there are holes on it.
    meta.solution().set(boroboroPat);

    return eater.mouth().toSeq();
  }

  public static final class InferMeta extends PatMatcher {
    public InferMeta(@NotNull UnaryOperator<Term> pre) { super(pre); }
    @Override protected void onMetaPat(@NotNull Pat pat, @NotNull MetaPatTerm term) throws MatcherBase.Failure {
      var maybeMeta = realSolution(term);
      if (maybeMeta instanceof MetaPatTerm(var meta)) {
        var bindsMetas = doSolveMeta(pat, meta);
        bindsMetas.forEach(this::onMatchBind);
      } else {
        match(pat, maybeMeta);
      }
    }
  }

  public static final class NoMeta extends PatMatcher {
    public NoMeta(@NotNull UnaryOperator<Term> pre) { super(pre); }
    @Override protected void onMetaPat(@NotNull Pat pat, @NotNull MetaPatTerm term) throws Failure {
      switch (realSolution(term)) {
        case MetaPatTerm _ -> throw new Failure(State.Stuck);
        case Term maybeMeta -> match(pat, maybeMeta);
      }
    }

    public @NotNull Term apply(
      @NotNull Term.Matching matching,
      @NotNull ImmutableSeq<Term> terms
    ) {
      try {
        matchMany(matching.patterns(), terms);
        return matching.body().instTele(matched.freeze().view());
      } catch (MatcherBase.Failure e) {
        return Panic.unreachable();
      }
    }
  }
}
