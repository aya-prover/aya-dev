// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.call;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableArrayList;
import kala.function.IndexedFunction;
import org.aya.generic.TermVisitor;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.marker.BetaRedex;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * @see BetaRedex#make(java.util.function.UnaryOperator)
 */
public sealed interface Callable extends Term permits MatchCall, Callable.Tele, MetaCall {
  @NotNull ImmutableSeq<@NotNull Term> args();

  static @NotNull ImmutableSeq<Term> descent(ImmutableSeq<Term> args, UnaryOperator<Term> f) {
    return descent(args, TermVisitor.ofTerm(f));
  }

  @Deprecated
  static @NotNull ImmutableSeq<Term> descent(ImmutableSeq<Term> args, IndexedFunction<Term, Term> f) {
    // return args.map(arg -> f.apply(0, arg));
    if (args.isEmpty()) return args;
    var ret = MutableArrayList.from(args);
    for (int i = 0; i < ret.size(); i++) {
      ret.set(i, f.apply(0, ret.get(i)));
    }
    return ret.toImmutableArray();
  }

  static @NotNull ImmutableSeq<Term> descent(@NotNull ImmutableSeq<Term> args, @NotNull TermVisitor visitor) {
    return args.map(visitor::term);
  }

  /**
   * Call to a {@link AnyDef}.
   */
  sealed interface Tele extends Callable permits SharableCall, ConCallLike, MemberCall, RuleReducer {
    @NotNull AnyDef ref();
    int ulift();
  }
  /** A call that allows {@code ourCall} field sharing when the args are always empty. */
  sealed interface SharableCall extends Tele permits ConCall, DataCall, FnCall, PrimCall { }
}
