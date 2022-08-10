// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.repr;

import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
public sealed interface CodeShape {
  record FnShape(
    @NotNull ImmutableSeq<ParamShape> tele
  ) implements CodeShape {}

  record DataShape(
    @NotNull ImmutableSeq<ParamShape> tele,
    @NotNull ImmutableSeq<CtorShape> ctors
  ) implements CodeShape {}

  record StructShape(
    @NotNull ImmutableSeq<ParamShape> tele,
    @NotNull ImmutableSeq<FieldShape> fields
  ) implements CodeShape {}

  record CtorShape(
    @NotNull ImmutableSeq<ParamShape> tele
  ) implements CodeShape {}

  record FieldShape(
    @NotNull ImmutableSeq<ParamShape> tele
  ) implements CodeShape {}

  /**
   * @author kiva
   */
  sealed interface TermShape {
    record Any() implements TermShape {}

    record Call(int superLevel) implements TermShape {}

    record TeleRef(int superLevel, int nth) implements TermShape {}
  }

  /**
   * @author kiva
   */
  sealed interface PatShape {
    record Any() implements PatShape {}
  }

  /**
   * @author kiva
   */
  sealed interface ParamShape {
    record Any() implements ParamShape {}

    record Licit(@NotNull CodeShape.TermShape type, boolean explicit) implements ParamShape {}

    record Optional(@NotNull CodeShape.ParamShape param) implements ParamShape {}

    static @NotNull CodeShape.ParamShape ex(@NotNull CodeShape.TermShape type) {
      return new Licit(type, true);
    }

    static @NotNull CodeShape.ParamShape im(@NotNull CodeShape.TermShape type) {
      return new Licit(type, false);
    }

    static @NotNull CodeShape.ParamShape anyEx() {
      return ex(new TermShape.Any());
    }

    static @NotNull CodeShape.ParamShape anyIm() {
      return im(new TermShape.Any());
    }
  }
}
