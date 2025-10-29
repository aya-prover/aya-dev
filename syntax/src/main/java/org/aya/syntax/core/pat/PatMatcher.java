// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.pat;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.FreezableMutableList;
import kala.collection.mutable.MutableList;
import kala.control.Result;
import org.aya.generic.State;
import org.aya.syntax.core.annotation.Bound;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.term.MetaPatTerm;
import org.aya.syntax.core.term.Term;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public abstract class PatMatcher extends MatcherBase {
  protected final @NotNull FreezableMutableList<@Closed Term> matched = FreezableMutableList.create();

  public PatMatcher(@NotNull UnaryOperator<@Closed Term> pre) { super(pre); }
  @Override
  protected abstract void onMetaPat(@Bound @NotNull Pat pat, @Closed @NotNull MetaPatTerm metaPatTerm) throws Failure;
  @Override
  protected void onMatchBind(Pat.@Bound @NotNull Bind bind, @Closed @NotNull Term matched) { onMatchBind(matched); }
  protected void onMatchBind(@NotNull Term matched) { this.matched.append(matched); }

  /// @param pats well-typed patterns, the **whole** pats should be [Closed].
  /// @return a substitution of corresponding bindings of {@param pats} if success.
  /// @apiNote The binding order is the same as [Pat#collectVariables]
  /// @implNote Note that we match a [Closed] term against to a [Bound] [Pat],
  /// this is expected as it is impossible to inst all pat before pat matching.
  /// However, we can inst the current [Pat] during matching by [PatMatcher#matched]
  /// @see State
  public @NotNull Result<ImmutableSeq<@Closed Term>, State> apply(
    @NotNull ImmutableSeq<@Bound Pat> pats,
    @NotNull ImmutableSeq<@Closed Term> terms
  ) {
    try {
      matchMany(pats, terms);
      return Result.ok(matched.toSeq());
    } catch (MatcherBase.Failure e) {
      return Result.err(e.reason);
    }
  }

  private static @Closed @NotNull Term realSolution(@Closed @NotNull MetaPatTerm term) {
    @Closed Pat pat = term.meta();
    while (pat instanceof Pat.@Closed Meta meta && meta.solution().get() instanceof Pat notNullPat)
      pat = notNullPat;
    return PatToTerm.visit(pat);
  }

  public @NotNull ImmutableSeq<@Closed Term> doSolveMeta(@Bound @NotNull Pat pat, Pat.@Closed @NotNull Meta meta) {
    assert meta.solution().get() == null;
    // No solution, set the current pattern as solution,
    // also replace the bindings in pat as sub-meta,
    // so that we can solve this meta more.

    // this is why no scope problem, we don't use those foreign [Pat.Bind] directly, instead, we replace them with [Pat.Meta],
    // and make fresh [LocalVar] for them when inlined.
    var eater = new BindEater(matched.toSeq(), MutableList.create());
    @Closed var boroboroPat = eater.apply(pat);   // It looks boroboro, there are holes on it.
    // meta is still Closed after `set`
    meta.solution().set(boroboroPat);

    return eater.mouth().toSeq();
  }

  public static final class InferMeta extends PatMatcher {
    public InferMeta(@NotNull UnaryOperator<Term> pre) { super(pre); }
    @Override
    protected void onMetaPat(@Bound @NotNull Pat pat, @Closed @NotNull MetaPatTerm term) throws MatcherBase.Failure {
      @Closed var maybeMeta = realSolution(term);
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
    @Override protected void onMetaPat(@Bound @NotNull Pat pat, @Closed @NotNull MetaPatTerm term) throws Failure {
      switch (realSolution(term)) {
        case MetaPatTerm _ -> throw new Failure(State.Stuck);
        case Term maybeMeta -> match(pat, maybeMeta);
      }
    }

    public @Closed @NotNull Term apply(
      @NotNull Term.Matching matching,
      @NotNull ImmutableSeq<@Closed Term> terms
    ) {
      try {
        matchMany(matching.patterns(), terms);
        // after a successful match, the following inst will give us a Closed term
        return matching.body().instTele(matched.freeze().view());
      } catch (MatcherBase.Failure e) {
        return Panic.unreachable();
      }
    }
  }
}
