// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.normalize;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.FreezableMutableList;
import kala.collection.mutable.MutableList;
import kala.control.Result;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.pat.PatToTerm;
import org.aya.generic.State;
import org.aya.syntax.core.term.MetaPatTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.TupTerm;
import org.aya.syntax.core.term.call.ConCall;
import org.aya.syntax.core.term.call.ConCallLike;
import org.aya.syntax.core.term.repr.IntegerTerm;
import org.aya.tyck.pat.BindEater;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 *
 */
public final class PatMatcher {
  private final boolean inferMeta;
  private final @NotNull UnaryOperator<Term> pre;
  private final @NotNull FreezableMutableList<Term> matched = FreezableMutableList.create();

  /**
   * @param inferMeta whether infer the PatMetaTerm
   */
  public PatMatcher(boolean inferMeta, @NotNull UnaryOperator<Term> pre) {
    this.inferMeta = inferMeta;
    this.pre = pre;
  }

  public static class Failure extends Throwable {
    public final State reason;

    private Failure(State reason) {
      super(null, null, false, false);
      this.reason = reason;
    }
  }

  /**
   * Match {@param term} against to {@param pat}
   *
   * @return a substitution of corresponding bindings of {@param pat} if success
   */
  private void match(@NotNull Pat pat, @NotNull Term term) throws Failure {
    switch (pat) {
      // We stuck on absurd patterns, as if this is reached, the term must have an empty type,
      // which we should be expecting to refute, not to compute on it.
      case Pat.Absurd _ -> throw new Failure(State.Stuck);
      case Pat.Bind _ -> onMatchBind(term);
      case Pat.Con con -> {
        switch (pre.apply(term)) {
          case ConCallLike kon -> {
            if (!con.ref().equals(kon.ref())) throw new Failure(State.Mismatch);
            matchMany(con.args(), kon.conArgs());
            // ^ arguments for data should not be matched
          }
          case MetaPatTerm metaPatTerm -> solve(pat, metaPatTerm);
          default -> throw new Failure(State.Stuck);
        }
      }
      case Pat.Tuple tuple -> {
        switch (pre.apply(term)) {
          case TupTerm tup -> matchMany(tuple.elements(), tup.items());
          case MetaPatTerm metaPatTerm -> solve(pat, metaPatTerm);
          default -> throw new Failure(State.Stuck);
        }
      }
      // You can't match with a tycking pattern!
      case Pat.Meta _ -> throw new Panic("Illegal pattern: Pat.Meta");
      case Pat.ShapedInt lit -> {
        switch (pre.apply(term)) {
          case IntegerTerm rit -> {
            if (lit.repr() != rit.repr()) throw new Failure(State.Mismatch);
            ImmutableSeq.empty();
          }
          case ConCall con -> match(lit.constructorForm(), con);
          // we only need to handle matching both literals, otherwise we just rematch it
          // with constructor form to reuse the code as much as possible (like solving MetaPats).
          case Term t -> match(lit.constructorForm(), t);
        }
      }
    }
    ;
  }

  private void onMatchBind(@NotNull Term matched) {
    this.matched.append(matched);
  }

  /**
   * @return a substitution of corresponding bindings of {@param pats} if success.
   * @apiNote The binding order is the same as {@link Pat#collectVariables}
   * @see State
   */
  public @NotNull Result<ImmutableSeq<Term>, State> apply(
    @NotNull ImmutableSeq<Pat> pats,
    @NotNull ImmutableSeq<Term> terms
  ) {
    try {
      matchMany(pats, terms);
      return Result.ok(matched.toImmutableSeq());
    } catch (Failure e) {
      return Result.err(e.reason);
    }
  }

  public @NotNull Result<Term, State> apply(
    @NotNull Term.Matching matching,
    @NotNull ImmutableSeq<Term> terms
  ) {
    try {
      matchMany(matching.patterns(), terms);
      return Result.ok(matching.body().instantiateTele(matched.freeze().view()));
    } catch (Failure e) {
      return Result.err(e.reason);
    }
  }

  /**
   * @see #match(Pat, Term)
   */
  private void matchMany(
    @NotNull ImmutableSeq<Pat> pats,
    @NotNull ImmutableSeq<Term> terms
  ) throws Failure {
    assert pats.sizeEquals(terms) : "List size mismatch 😱";
    pats.forEachWithChecked(terms, this::match);
  }

  private void solve(@NotNull Pat pat, @NotNull MetaPatTerm term) throws Failure {
    var maybeMeta = realSolution(term);
    if (maybeMeta instanceof MetaPatTerm meta) {
      if (inferMeta) {
        var bindsMetas = doSolveMetaPrime(pat, meta.meta());
        bindsMetas.forEach(this::onMatchBind);
      }
      else throw new Failure(State.Stuck);
    } else {
      match(pat, maybeMeta);
    }
  }

  public static @NotNull Term realSolution(@NotNull MetaPatTerm term) {
    Pat pat = term.meta();
    while (pat instanceof Pat.Meta meta && meta.solution().get() instanceof Pat notNullPat) pat = notNullPat;
    return PatToTerm.visit(pat);
  }

  public @NotNull ImmutableSeq<Term> doSolveMetaPrime(@NotNull Pat pat, Pat.Meta meta) {
    assert meta.solution().get() == null;
    // No solution, set the current pattern as solution,
    // also replace the bindings in pat as sub-meta,
    // so that we can solve this meta more.

    var eater = new BindEater(matched.toImmutableSeq(), MutableList.create());
    var boroboroPat = eater.apply(pat);   // It looks boroboro, there are holes on it.
    meta.solution().set(boroboroPat);

    return eater.mouth().toImmutableSeq();
  }

  /**
   * Perform meta solving, make sure that {@param meta} is unsolved.
   */
  public static @NotNull ImmutableSeq<Term> doSolveMeta(@NotNull Pat pat, Pat.Meta meta) {
    assert meta.solution().get() == null;
    // No solution, set the current pattern as solution,
    // also replace the bindings in pat as sub-meta,
    // so that we can solve this meta more.

    // TODO
    var eater = new BindEater(ImmutableSeq.empty(), MutableList.create());
    var boroboroPat = eater.apply(pat);   // It looks boroboro, there are holes on it.
    meta.solution().set(boroboroPat);

    return eater.mouth().toImmutableSeq();
  }
}
