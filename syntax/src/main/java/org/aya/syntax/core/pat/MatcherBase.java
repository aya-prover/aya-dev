// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.pat;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.State;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.term.MetaPatTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.TupTerm;
import org.aya.syntax.core.term.call.ConCall;
import org.aya.syntax.core.term.call.ConCallLike;
import org.aya.syntax.core.term.repr.IntegerTerm;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public abstract class MatcherBase {
  private final @NotNull UnaryOperator<@Closed Term> pre;
  protected MatcherBase(@NotNull UnaryOperator<@Closed Term> pre) { this.pre = pre; }

  /// Match {@param term} against to {@param pat},
  /// produces substitution of corresponding bindings of {@param pat} in [#matched] if success.
  ///
  /// The dblity of [Pat] is unknown, this depends on the implementation and the usage.
  /// @see PatMatcher
  protected void match(@NotNull Pat pat, @Closed @NotNull Term term) throws Failure {
    switch (pat) {
      // We stuck on absurd patterns, as if this is reached, the term must have an empty type,
      // which we should be expecting to refute, not to compute on it.
      case Pat.Misc misc -> {
        switch (misc) {
          case Absurd -> throw new Failure(State.Stuck);
          // case UntypedBind -> onMatchBind(term);
        }
      }
      case @Closed Pat.Bind bind -> onMatchBind(bind, term);
      case Pat.Con con -> {
        switch (pre.apply(term)) {
          case ConCallLike kon -> {
            if (!con.ref().equals(kon.ref())) throw new Failure(State.Mismatch);
            matchMany(con.args(), kon.conArgs());
            // ^ arguments for data should not be matched
          }
          case MetaPatTerm metaPatTerm -> onMetaPat(pat, metaPatTerm);
          default -> throw new Failure(State.Stuck);
        }
      }
      case Pat.Tuple(@Closed var l, @Closed var r) -> {
        switch (pre.apply(term)) {
          case TupTerm(var ll, var rr) -> {
            match(l, ll);
            match(r, rr);
          }
          case MetaPatTerm metaPatTerm -> onMetaPat(pat, metaPatTerm);
          default -> throw new Failure(State.Stuck);
        }
      }
      // You can't match with a tycking pattern!
      case Pat.Meta _ -> throw new Panic("Illegal pattern: Pat.Meta");
      case @Closed Pat.ShapedInt lit -> {
        switch (pre.apply(term)) {
          case IntegerTerm rit -> {
            if (lit.repr() != rit.repr()) throw new Failure(State.Mismatch);
          }
          case ConCall con -> match(lit.constructorForm(), con);
          // we only need to handle matching both literals, otherwise we just rematch it
          // with constructor form to reuse the code as much as possible (like solving MetaPats).
          case Term t -> match(lit.constructorForm(), t);
        }
      }
    }
  }
  protected abstract void onMetaPat(@NotNull Pat pat, @Closed @NotNull MetaPatTerm metaPatTerm) throws Failure;
  protected abstract void onMatchBind(Pat.@NotNull Bind bind, @Closed @NotNull Term matched);
  /**
   * @see #match(Pat, Term)
   */
  protected void matchMany(
    @NotNull ImmutableSeq<Pat> pats,
    @NotNull ImmutableSeq<@Closed Term> terms
  ) throws Failure {
    assert pats.sizeEquals(terms) : "List size mismatch 😱";
    pats.forEachWithChecked(terms, this::match);
  }

  public static final class Failure extends Throwable {
    public final State reason;

    public Failure(State reason) {
      super(null, null, false, false);
      this.reason = reason;
    }
  }
}
