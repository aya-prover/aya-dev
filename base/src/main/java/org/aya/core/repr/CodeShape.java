// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.repr;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import org.jetbrains.annotations.NotNull;

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
    ZERO, SUC, NIL, CONS, NAT,
  }

  record FnShape(
    @NotNull ImmutableSeq<ParamShape> tele,
    @NotNull TermShape result,
    @NotNull Either<TermShape, ImmutableSeq<ClauseShape>> body
  ) implements CodeShape {}

  record ClauseShape(@NotNull ImmutableSeq<PatShape> pats, @NotNull TermShape body) implements CodeShape {
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

  default @NotNull Named named(@NotNull String name) {
    return new Named(name, this);
  }
}
