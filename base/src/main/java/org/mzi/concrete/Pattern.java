// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete;

import org.glavo.kala.Tuple2;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * @author kiva
 */
public sealed interface Pattern {
  sealed interface Atom {
  }

  record Tuple(@NotNull Buffer<Pattern> patterns) implements Atom {
  }

  record Braced(@NotNull Buffer<Pattern> patterns) implements Atom {
  }

  record Number(int number) implements Atom {
  }

  record CalmFace() implements Atom {
  }

  /**
   * This variant of Atom only appears in {@link PatCtor#params}
   */
  record Ident(@NotNull String id) implements Atom {
  }

  record PatAtom(
    @NotNull Atom atom,
    @Nullable Tuple2<@NotNull String, @NotNull Expr> as
  ) implements Pattern {
  }

  record PatCtor(
    @NotNull String name,
    @NotNull Buffer<Atom> params,
    @Nullable String as,
    @Nullable Expr type
  ) implements Pattern {
  }
}
