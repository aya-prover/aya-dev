// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core;

import kala.value.LazyValue;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.term.SortTerm;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public sealed interface Jdg {
  @NotNull Term wellTyped();
  @NotNull Term type();

  /**
   * @apiNote the mapper {@param f} may not execute immediately, so make sure that {@param f} is pure.
   */
  @NotNull Jdg map(@NotNull UnaryOperator<Term> f);
  default Jdg lift(int lift) { return map(t -> t.elevate(lift)); }

  /**
   * {@link Default#type} is the type of {@link Default#wellTyped}.
   */
  record Default(@Override @NotNull Term wellTyped, @Override @NotNull Term type) implements Jdg {
    @Override public @NotNull Default map(@NotNull UnaryOperator<Term> f) {
      return new Default(f.apply(wellTyped), f.apply(type));
    }
  }

  record Sort(@Override @NotNull SortTerm wellTyped) implements Jdg {
    @Override public @NotNull SortTerm type() { return wellTyped.succ(); }
    @Override public @NotNull Jdg map(@NotNull UnaryOperator<Term> f) {
      return this;
    }
  }

  record Lazy(@Override @NotNull Term wellTyped, @NotNull LazyValue<Term> lazyType) implements Jdg {
    @Override public @NotNull Term type() { return lazyType.get(); }
    @Override public @NotNull Lazy map(@NotNull UnaryOperator<Term> f) {
      return new Lazy(f.apply(wellTyped), lazyType.map(f));
    }
  }

  record TypeMissing(@Override @NotNull Term wellTyped) implements Jdg {
    public static @Closed TypeMissing of(@Closed Term wellTyped) {
      return new TypeMissing(wellTyped);
    }

    @Override public @NotNull Term type() { throw new UnsupportedOperationException("type missing"); }
    @Override public @NotNull TypeMissing map(@NotNull UnaryOperator<Term> f) {
      return new TypeMissing(f.apply(wellTyped));
    }
  }
}
