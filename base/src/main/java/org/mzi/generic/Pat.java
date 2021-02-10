// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.generic;

import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.ref.LocalVar;


/**
 * @author kiva
 */
public sealed interface Pat<Term> {
  @Nullable LocalVar as();
  @NotNull Term type();

  record PatAtom<Term>(
    @NotNull Atom<Pat<Term>> atom,
    @Nullable LocalVar as,
    @NotNull Term type
  ) implements Pat<Term> {
  }

  record PatCtor<Term>(
    @NotNull String name,
    @NotNull Buffer<Atom<Pat<Term>>> params,
    @Nullable LocalVar as,
    @NotNull Term type
  ) implements Pat<Term> {
  }

  /**
   * pattern remains unresolved because we are unable to know
   * whether `zero` is a data ctor or a bind id
   */
  record UnresolvedPat<Term>(
    @NotNull Atom<Pat<Term>> name,
    @NotNull Buffer<Atom<Pat<Term>>> params,
    @Nullable LocalVar as,
    @NotNull Term type
  ) implements Pat<Term> {
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
  public sealed interface Clause<Term> {
    record Possible<Term>(
      @NotNull Buffer<Pat<Term>> patterns,
      @NotNull Term expr
    ) implements Clause<Term> {
    }

    record Impossible<Term>() implements Clause<Term> {
    }
  }
}
