// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core;

import kala.function.IndexedFunction;
import org.aya.syntax.core.annotation.Bound;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.LocalTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.GenerateKind;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * An "abstract" Lambda Term.<br/>
 * We also use it to represent a term that contains a "free" {@link LocalTerm},
 * so we can handle them in a scope-safe manner.
 * Note that you shouldn't supply a {@link LocalTerm} to "DeBruijn Index"-lize a {@link Closure},
 * since it may contain another {@link Closure}, the safe way is to supply a {@link FreeTerm} then bind it,
 * see {@link Jit#toLocns()}
 */
public sealed interface Closure extends UnaryOperator<Term> {
  static @NotNull Closure mkConst(@NotNull Term term) { return new Const(term); }
  Closure descent(IndexedFunction<Term, Term> f);

  /**
   * Corresponds to <emph>instantiate</emph> operator in [MM 2004],
   * <emph>instantiate</emph> the body with given {@param term}.
   * Called <code>apply</code> due to Mini-TT.
   */
  @Override Term apply(Term term);
  default @NotNull Term apply(LocalVar var) { return apply(new FreeTerm(var)); }
  @NotNull Closure.Locns toLocns();

  default @NotNull Closure reapply(UnaryOperator<Term> f) {
    var fresh = new LocalVar("_", SourcePos.NONE, GenerateKind.Basic.Tyck);
    return f.apply(apply(fresh)).bind(fresh);
  }

  record Const(@NotNull Term term) implements Closure {
    @Override public Closure descent(IndexedFunction<Term, Term> f) {
      var result = f.apply(1, term);
      if (result == term) return this;
      return new Const(result);
    }
    @Override public Term apply(Term ignored) { return term; }
    @Override public @NotNull Closure.Locns toLocns() { return new Locns(term); }
  }

  /**
   * We do sometimes need to {@link #descent} into the body immediately,
   * because sometimes descent have side-effects. An example is find-usages in meta resolution,
   * it relies on descent and counting the number of free vars along the way.
   * So it is important to immediately descent into the body, which we do so using {@link #toLocns()}.
   */
  record Jit(@NotNull UnaryOperator<Term> lam) implements Closure {
    @Override public @NotNull Closure.Locns toLocns() {
      var antiMatter = new LocalVar("matter");
      return lam.apply(new FreeTerm(antiMatter)).bind(antiMatter);
    }
    @Override public Closure descent(IndexedFunction<Term, Term> f) { return toLocns().descent(f); }
    @Override public Term apply(Term term) { return lam.apply(term); }
  }

  record Locns(@Bound Term body) implements Closure {
    @Override public Closure descent(IndexedFunction<Term, Term> f) {
      var result = f.apply(1, body);
      if (result == body) return this;
      return new Locns(result);
    }

    @Override public Term apply(Term term) { return body.instantiate(term); }
    @Override public @NotNull Closure.Locns toLocns() { return this; }
  }
}
