// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.function.IndexedFunction;
import kala.tuple.primitive.IntObjTuple2;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.aya.syntax.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

public record LamTerm(Closure body) implements StableWHNF {
  public LamTerm(Term indexedBody) {
    this(new Closure.Idx(indexedBody));
  }
  @Override public @NotNull LamTerm descent(@NotNull IndexedFunction<Term, Term> f) {
    var result = body.descent(f);
    if (result == body) return this;
    return new LamTerm(result);
  }

  public static @NotNull Term make(int paramSize, @NotNull Term body) {
    for (var i = 0; i < paramSize; ++i) body = new LamTerm(body);
    return body;
  }

  /**
   * Unwrap a {@link LamTerm} as much as possible
   *
   * @return an integer indicates how many bindings are introduced
   * and a most inner term that is not a {@link LamTerm}.
   */
  public static @NotNull IntObjTuple2<Term> unwrap(@NotNull Term term) {
    int params = 0;
    var it = term;

    while (it instanceof LamTerm lamTerm) {
      params = params + 1;
      it = lamTerm.body.apply(new FreeTerm(LocalVar.generate(String.valueOf(params))));
    }

    return IntObjTuple2.of(params, it);
  }
}
