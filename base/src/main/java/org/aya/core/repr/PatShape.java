// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.repr;

import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

// TODO[h]: Licit, we can use generalized ParamShape

/**
 * @author kiva
 */
sealed public interface PatShape {
  enum Any implements PatShape {
    INSTANCE;
  }

  enum Bind implements PatShape {
    INSTANCE;
  }

  record Ctor(@NotNull ImmutableSeq<PatShape> innerPats) implements PatShape {
  }

  /**
   * Expecting a certain ctor, {@link ShapeMatcher} will crash if the {@param name} is invalid (such as undefined or not a DataShape)
   *
   * @param name a reference to a {@link CodeShape.DataShape}d term
   * @param id   the {@link CodeShape.MomentId} to the ctor
   */
  record ShapedCtor(@NotNull String name, @NotNull CodeShape.MomentId id,
                    @NotNull ImmutableSeq<PatShape> innerPats) implements PatShape {}

  record Named(@NotNull String name, @NotNull PatShape pat) implements PatShape {}

  default @NotNull Named named(@NotNull String name) {
    return new Named(name, this);
  }
}
