// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.collection.Seq;
import kala.collection.mutable.MutableList;
import org.aya.generic.Renamer;
import org.aya.generic.TermVisitor;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.term.marker.BindingIntro;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.aya.syntax.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

public record LamTerm(Closure body) implements StableWHNF, BindingIntro {
  public static final LamTerm ID = new LamTerm(new Closure.Jit(t -> t));
  public LamTerm(Term indexedBody) {
    this(new Closure.Locns(indexedBody));
  }
  @Override
  public @NotNull Term descent(@NotNull TermVisitor visitor) {
    var result = visitor.closure(body);
    if (result == body) return this;
    return new LamTerm(result);
  }

  public static @NotNull Term make(int paramSize, @NotNull Term body) {
    for (var i = 0; i < paramSize; ++i) body = new LamTerm(body);
    return body;
  }

  public record Unlam(@NotNull Seq<LocalVar> params, @NotNull Term body) { }

  /**
   * Unwrap a {@link LamTerm} as much as possible
   *
   * @return an integer indicates how many bindings are introduced
   * and a most inner term that is not a {@link LamTerm}.
   */
  public static @NotNull Unlam unlam(@NotNull Term term, @NotNull Renamer nameGen) {
    var params = MutableList.<LocalVar>create();
    var it = term;

    while (it instanceof LamTerm(var lam)) {
      var name = nameGen.bindName("p" + params.size());
      params.append(name);
      it = lam.apply(new FreeTerm(name));
    }

    return new Unlam(params, it);
  }
}
