// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.call;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.generic.TermVisitor;
import org.aya.syntax.core.def.MatchyLike;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

/**
 * For JIT-compiled matchies, we do not need to {@link #descent} into the clauses,
 * because all we need actually is to descent the {@link #captures},
 * like for find-usages in the meta resolution, just counting the captures is enough.
 * I don't have a strong proof of this but I am reasonably confident.
 * <p>
 * For non-JIT compiled matchies, we do need to descent into the body because we
 * need to solve the metas inside them, but we do not need to, e.g.
 * substitute inside the clauses. So we do not traverse clauses in {@link #descent},
 * and special case on match calls in {@link org.aya.normalize.Finalizer}.
 */
public record MatchCall(
  @NotNull MatchyLike ref,
  @Override @NotNull ImmutableSeq<Term> args,
  @Override @NotNull ImmutableSeq<Term> captures
) implements Callable {
  public @NotNull MatchCall update(
    ImmutableSeq<Term> newArgs, ImmutableSeq<Term> newCaptures,
    MatchyLike newClauses
  ) {
    return this.args.sameElements(newArgs, true)
      && this.captures.sameElements(newCaptures, true)
      && this.ref == newClauses
      ? this : new MatchCall(newClauses, newArgs, newCaptures);
  }

  @Override public @NotNull Term descent(@NotNull TermVisitor visitor) {
    var newArgs = Callable.descent(args, visitor);
    var newCaptures = Callable.descent(captures, visitor);
    return update(newArgs, newCaptures, ref);
  }
}
