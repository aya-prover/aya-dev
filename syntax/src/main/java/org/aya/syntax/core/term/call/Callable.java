// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.call;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableArrayList;
import kala.function.IndexedFunction;
import org.aya.syntax.concrete.stmt.decl.Decl;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.term.AppTerm;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

/**
 * @see AppTerm#make()
 */
public sealed interface Callable extends Term permits Callable.Tele, MetaCall {
  @NotNull ImmutableSeq<@NotNull Term> args();

  static @NotNull ImmutableSeq<Term> descent(ImmutableSeq<Term> args, IndexedFunction<Term, Term> f) {
    // return args.map(arg -> f.apply(0, arg));
    var ret = MutableArrayList.from(args);
    for (int i = 0; i < ret.size(); i++) {
      ret.set(i, f.apply(0, ret.get(i)));
    }
    return ret.toImmutableArray();
  }

  /**
   * Call to a {@link Decl}.
   */
  sealed interface Tele extends Callable permits ConCallLike, DataCall, FnCall, PrimCall, RuleReducer {
    @NotNull AnyDef ref();
    int ulift();
  }
}
