// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.repr;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.term.CallTerm;
import org.aya.core.term.Term;
import org.aya.generic.SortKind;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    /**
     * @param superLevel the data def reference
     * @param args       corresponds to {@link CallTerm.Data#args()}
     */
    record Call(int superLevel, @NotNull ImmutableSeq<TermShape> args) implements TermShape {
      @Contract("_ -> new") public static @NotNull Call justCall(int superLevel) {
        return new Call(superLevel, ImmutableSeq.empty());
      }
    }

    record TeleRef(int superLevel, int nth) implements TermShape {}

    /**
     * The shape to Sort term, I am not very work well at type theory, so improve this feel free!
     *
     * @param kind  the SortKind, null if accept any kind of sort. see {@link ShapeMatcher#matchTerm(TermShape, Term)}
     * @param lift I don't know.
     * @author hoshino
     */
    record Sort(@Nullable SortKind kind, int lift) implements TermShape {}
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
    // TODO[hoshino]: a better name is requested!
    enum Explicit {
      Any,
      Explicit,
      Implicit
    }

    record Any() implements ParamShape {}

    record Licit(@NotNull CodeShape.TermShape type, Explicit explicit) implements ParamShape {}

    record Optional(@NotNull CodeShape.ParamShape param) implements ParamShape {}

    static @NotNull CodeShape.ParamShape ex(@NotNull CodeShape.TermShape type) {
      return new Licit(type, Explicit.Explicit);
    }

    static @NotNull CodeShape.ParamShape im(@NotNull CodeShape.TermShape type) {
      return new Licit(type, Explicit.Implicit);
    }

    static @NotNull CodeShape.ParamShape any(@NotNull CodeShape.TermShape type) {
      return new Licit(type, Explicit.Any);
    }

    static @NotNull CodeShape.ParamShape anyEx() {
      return ex(new TermShape.Any());
    }

    static @NotNull CodeShape.ParamShape anyIm() {
      return im(new TermShape.Any());
    }
    static @NotNull CodeShape.ParamShape anyLicit() {
      return any(new TermShape.Any());
    }
  }
}
