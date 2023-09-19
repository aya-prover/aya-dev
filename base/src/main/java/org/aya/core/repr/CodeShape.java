// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.repr;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
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
    @NotNull ImmutableSeq<ParamShape> tele,
    @NotNull TermShape result,
    @NotNull Either<TermShape, ImmutableSeq<ClauseShape>> body
  ) implements CodeShape {}

  record ClauseShape(@NotNull ImmutableSeq<ImmutableSeq<PatShape>> pats, @NotNull TermShape body) implements CodeShape {
  }

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

  record Named(@NotNull String name, @NotNull CodeShape shape) implements CodeShape {
  }

  default @NotNull CodeShape.TermShape.Named named(@NotNull String name) {
    return new TermShape.Named(name, this);
  }

  /**
   * @author kiva
   */
  sealed interface TermShape {
    enum Any implements TermShape {
      INSTANCE;
    }

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

    record Named(@NotNull String name, @NotNull TermShape shape) implements TermShape {}

    record NameRef(@NotNull String name) implements TermShape {}

    record Shape(@NotNull CodeShape shape) implements TermShape {}

    default @NotNull Named named(@NotNull String name) {
      return new Named(name, this);
    }
  }

  // TODO[h]: Licit, we can use generalized ParamShape
  /**
   * @author kiva
   */
  sealed interface PatShape {
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
     * @param name a reference to a {@link DataShape}d term
     * @param id   the {@link MomentId} to the ctor
     */
    record ShapedCtor(@NotNull String name, @NotNull MomentId id,
                      @NotNull ImmutableSeq<PatShape> innerPats) implements PatShape {}

    record Named(@NotNull String name, @NotNull PatShape pat) implements PatShape {}

    default @NotNull Named named(@NotNull String name) {
      return new Named(name, this);
    }
  }

  /**
   * @author kiva
   */
  sealed interface ParamShape {
    enum Any implements ParamShape {
      INSTANCE;
    }

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
      return explicit(TermShape.Any.INSTANCE);
    }

    static @NotNull CodeShape.ParamShape anyIm() {
      return implicit(TermShape.Any.INSTANCE);
    }

    // anyLicit(TermShape.Any) would be equivalent to ParamShape.Any
  }
}
