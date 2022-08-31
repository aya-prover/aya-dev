// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import kala.collection.immutable.ImmutableSeq;
import org.aya.guest0x0.cubical.Restr;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Generalized 'partial element' syntax.
 *
 * @see org.aya.core.term.IntroTerm.PartEl
 * @see org.aya.concrete.Expr.PartEl
 */
public sealed interface Partial<Term extends Restr.TermLike<Term>> {
  /** faces filled by this partial element */
  @NotNull Restr<Term> restr();
  @NotNull Partial<Term> map(@NotNull Function<Term, Term> mapper);
  void forEach(@NotNull Consumer<Term> consumer);

  /** I am happy because I have (might be) missing faces. Same as <code>ReallyPartial</code> in guest0x0 */
  record Happy<Term extends Restr.TermLike<Term>>(
    @NotNull ImmutableSeq<Restr.Side<Term>> clauses
  ) implements Partial<Term> {
    @Override public @NotNull Restr<Term> restr() {
      return new Restr.Vary<>(clauses.map(Restr.Side::cof));
    }

    @Override public @NotNull Happy<Term> map(@NotNull Function<Term, Term> mapper) {
      var cl = clauses.map(c -> c.rename(mapper));
      if (cl.sameElements(clauses, true)) return this;
      return new Happy<>(cl);
    }

    @Override public void forEach(@NotNull Consumer<Term> consumer) {
      clauses.forEach(c -> {
        c.cof().view().forEach(consumer);
        consumer.accept(c.u());
      });
    }
  }

  /** I am sad because I am not partial. Same as <code>SomewhatPartial</code> in guest0x0 */
  record Sad<Term extends Restr.TermLike<Term>>(@NotNull Term u) implements Partial<Term> {
    @Override public @NotNull Restr<Term> restr() {
      return new Restr.Const<>(true);
    }

    @Override public @NotNull Partial.Sad<Term> map(@NotNull Function<Term, Term> mapper) {
      var u = mapper.apply(this.u);
      if (u == this.u) return this;
      return new Sad<>(u);
    }

    @Override public void forEach(@NotNull Consumer<Term> consumer) {
      consumer.accept(u);
    }
  }
}
