// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.call;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.syntax.compile.JitMatchy;
import org.aya.syntax.core.def.Matchy;
import org.aya.syntax.core.def.MatchyLike;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

/**
 * For JIT-compiled matchies, we do not need to {@link #descent} into the clauses,
 * because all we need actually is to descent the {@link #captures},
 * like for find-usages in the meta resolution, just counting the captures is enough.
 * I don't have a strong proof of this but I am reasonably confident.
 * For non-JIT compiled matchies, we do need to descent into the body because we
 * need to solve the metas inside them.
 */
public record MatchCall(
  @NotNull MatchyLike ref,
  @Override @NotNull ImmutableSeq<Term> args,
  @Override @NotNull ImmutableSeq<Term> captures
) implements Callable {
  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    var newArgs = args.map(x -> f.apply(0, x));
    var newCaptures = captures.map(x -> f.apply(0, x));
    var newClauses = switch (ref) {
      case JitMatchy jit -> jit;
      case Matchy matchy -> matchy.descent(f);
    };
    return this.args.sameElements(newArgs, true)
      && this.captures.sameElements(newCaptures, true)
      && this.ref == newClauses
      ? this : new MatchCall(newClauses, newArgs, newCaptures);
  }
}
