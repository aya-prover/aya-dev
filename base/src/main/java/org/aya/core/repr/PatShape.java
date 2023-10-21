// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.repr;

import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

// TODO[h]: Licit, we can use generalized ParamShape

/**
 * @author kiva
 */
public sealed interface PatShape {
  enum Any implements PatShape {
    INSTANCE
  }

  record Bind(@NotNull CodeShape.LocalId name) implements PatShape, CodeShape.Moment {
  }

  sealed interface CtorLike extends PatShape permits Ctor, ShapedCtor {
    @NotNull ImmutableSeq<PatShape> innerPats();
  }

  record Ctor(@NotNull ImmutableSeq<PatShape> innerPats) implements CtorLike {
  }

  /**
   * Expecting a certain ctor, {@link ShapeMatcher} will crash
   * if the {@param dataId} is invalid (such as undefined or not a DataShaped thing)
   *
   * @param dataId a reference to a {@link CodeShape.DataShape}d term
   * @param ctorId the {@link CodeShape.GlobalId} to the ctor
   */
  record ShapedCtor(@NotNull CodeShape.MomentId dataId, @NotNull CodeShape.GlobalId ctorId,
                    @NotNull ImmutableSeq<PatShape> innerPats) implements CtorLike {
    public static @NotNull ShapedCtor of(@NotNull CodeShape.MomentId name, @NotNull CodeShape.GlobalId id) {
      return new ShapedCtor(name, id, ImmutableSeq.empty());
    }
  }
}
