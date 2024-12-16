// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.call;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.syntax.core.def.MatchyLike;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

public record MatchCall(
  @NotNull MatchyLike clauses,
  @Override @NotNull ImmutableSeq<Term> args,
  @Override @NotNull ImmutableSeq<Term> captures
) implements Callable {
  public @NotNull MatchCall update(
    @NotNull ImmutableSeq<Term> args,
    @NotNull ImmutableSeq<Term> captures
  ) {
    return this.args.sameElements(args, true)
      && this.captures.sameElements(captures, true)
      ? this : new MatchCall(clauses, args, captures);
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(
      args.map(x -> f.apply(0, x)),
      captures.map(x -> f.apply(0, x)
      ));
  }
}
