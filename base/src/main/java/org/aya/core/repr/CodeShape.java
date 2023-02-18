// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.repr;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.term.DataCall;
import org.aya.core.term.Term;
import org.aya.generic.SortKind;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

/**
 * @author kiva
 */
public sealed interface CodeShape {
  /** A capture group, see {@link CodeShape.CtorShape} and {@link ShapeMatcher#captures()} */
  sealed interface Moment {
    @NotNull MomentId name();
  }

  /** Typed capture name, rather than plain strings */
  enum MomentId implements Serializable {
    ZERO, SUC, NIL, CONS,
  }

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
    @NotNull MomentId name,
    @NotNull ImmutableSeq<ParamShape> tele
  ) implements CodeShape, Moment {}

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
     * @param args       corresponds to {@link DataCall#args()}
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
     * @param ulift the lower bound of the type level.
     * @author hoshino
     */
    record Sort(@Nullable SortKind kind, int ulift) implements TermShape {}
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

    record Licit(@NotNull CodeShape.TermShape type, Kind kind) implements ParamShape {
      enum Kind {
        Any, Ex, Im
      }
    }

    record Optional(@NotNull CodeShape.ParamShape param) implements ParamShape {}

    static @NotNull CodeShape.ParamShape explicit(@NotNull CodeShape.TermShape type) {
      return new Licit(type, Licit.Kind.Ex);
    }

    static @NotNull CodeShape.ParamShape implicit(@NotNull CodeShape.TermShape type) {
      return new Licit(type, Licit.Kind.Im);
    }

    static @NotNull CodeShape.ParamShape anyLicit(@NotNull CodeShape.TermShape type) {
      return new Licit(type, Licit.Kind.Any);
    }

    static @NotNull CodeShape.ParamShape anyEx() {
      return explicit(new TermShape.Any());
    }

    static @NotNull CodeShape.ParamShape anyIm() {
      return implicit(new TermShape.Any());
    }

    // anyLicit(TermShape.Any) would be equivalent to ParamShape.Any
  }
}
