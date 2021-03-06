// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.generic;

import org.aya.core.term.Term;
import org.aya.ref.LocalVar;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * @author kiva
 */
public sealed interface Pat<T> {
  @Nullable LocalVar as();
  @NotNull T type();
  <P, R> R accept(@NotNull Visitor<T, P, R> visitor, P p);
  default @NotNull Term toTerm() {
    throw new UnsupportedOperationException();
  }

  interface Visitor<Term, P, R> {
    R visitAtomic(@NotNull Atomic<Term> atomic, P p);
    R visitCtor(@NotNull Ctor<Term> ctor, P p);
    R visitUnresolved(@NotNull Unresolved<Term> unresolved, P p);
  }

  record Atomic<T>(
    @NotNull Atom<Pat<T>> atom,
    @Nullable LocalVar as,
    @NotNull T type
  ) implements Pat<T> {
    @Override public <P, R> R accept(@NotNull Visitor<T, P, R> visitor, P p) {
      return visitor.visitAtomic(this, p);
    }
  }

  record Ctor<T>(
    @NotNull String name,
    @NotNull Buffer<Pat<T>> params,
    @Nullable LocalVar as,
    @NotNull T type
  ) implements Pat<T> {
    @Override public <P, R> R accept(@NotNull Visitor<T, P, R> visitor, P p) {
      return visitor.visitCtor(this, p);
    }
  }

  /**
   * pattern remains unresolved because we are unable to know
   * whether `zero` is a data ctor or a bind id
   */
  record Unresolved<Term>(
    @NotNull Atom<Pat<Term>> name,
    @NotNull Buffer<Atom<Pat<Term>>> params,
    @Nullable LocalVar as,
    @NotNull Term type
  ) implements Pat<Term> {
    @Override public <P, R> R accept(@NotNull Visitor<Term, P, R> visitor, P p) {
      return visitor.visitUnresolved(this, p);
    }
  }

  /**
   * @author kiva
   */
  sealed interface Atom<Pat> {
    record Tuple<Pat>(@NotNull Buffer<Pat> patterns) implements Atom<Pat> {
    }

    record Braced<Pat>(@NotNull Buffer<Pat> patterns) implements Atom<Pat> {
    }

    record Number<Pat>(int number) implements Atom<Pat> {
    }

    record CalmFace<Pat>() implements Atom<Pat> {
    }

    record Bind<Pat>(@NotNull LocalVar bind) implements Atom<Pat> {
    }
  }

  /**
   * @author kiva
   */
  sealed interface Clause<Term> {
    <P, R> R accept(@NotNull Visitor<Term, P, R> visitor, P p);

    interface Visitor<Term, P, R> {
      R visitMatch(@NotNull Match<Term> match, P p);
      R visitAbsurd(@NotNull Absurd<Term> absurd, P p);
    }

    record Match<Term>(
      @NotNull Buffer<Pat<Term>> patterns,
      @NotNull Term expr
    ) implements Clause<Term> {
      @Override public <P, R> R accept(@NotNull Visitor<Term, P, R> visitor, P p) {
        return visitor.visitMatch(this, p);
      }
    }

    record Absurd<Term>() implements Clause<Term> {
      @Override public <P, R> R accept(@NotNull Visitor<Term, P, R> visitor, P p) {
        return visitor.visitAbsurd(this, p);
      }
    }
  }
}
