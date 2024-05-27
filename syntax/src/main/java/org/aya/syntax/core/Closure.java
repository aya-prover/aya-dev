// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core;

import kala.function.IndexedFunction;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.LocalTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * An "abstract" Lambda Term.<br/>
 * We also use it to represent a term that contains a "free" {@link org.aya.syntax.core.term.LocalTerm},
 * so we can handle them in a scope-safe manner.
 * Note that you shouldn't supply a {@link LocalTerm} to "DeBruijn Index"-lize a {@link Closure},
 * since it may contain another {@link Closure}, the safe way is to supply a {@link FreeTerm} then bind it,
 * see {@link Jit#toLam()}
 */
public sealed interface Closure extends UnaryOperator<Term> {
  static @NotNull Closure mkConst(@NotNull Term term) { return new Jit(_ -> term); }
  Closure descent(IndexedFunction<Term, Term> f);

  /**
   * Corresponds to <emph>instantiate</emph> operator in [MM 2004],
   * <emph>instantiate</emph> the body with given {@param term}.
   * Called <code>apply</code> due to Mini-TT.
   */
  @Override Term apply(Term term);
  default @NotNull Term apply(LocalVar var) { return apply(new FreeTerm(var)); }

  // NbE !!!!!!
  record Jit(@NotNull UnaryOperator<Term> lam) implements Closure {
    public @NotNull Idx toLam() {
      var antiMatter = new LocalVar("matter");
      return lam.apply(new FreeTerm(antiMatter)).bind(antiMatter);
    }
    @Override public Closure descent(IndexedFunction<Term, Term> f) { return toLam().descent(f); }
    @Override public Term apply(Term term) { return lam.apply(term); }
  }

  record Idx(Term body) implements Closure {
    @Override public Closure descent(IndexedFunction<Term, Term> f) {
      var result = f.apply(1, body);
      if (result == body) return this;
      return new Idx(result);
    }

    @Override public Term apply(Term term) { return body.instantiate(term); }
  }
}
