// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.repr;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.term.Term;
import org.aya.generic.SortKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author kiva
 */
sealed public interface TermShape {
  enum Any implements TermShape {
    INSTANCE;
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

  /**
   * Binding {@param name} to {@param shape}.
   */
  record Named(@NotNull String name, @NotNull TermShape shape) implements TermShape {}

  sealed interface Callable extends TermShape {
    @NotNull ImmutableSeq<TermShape> args();
  }

  record NameCall(@NotNull String name, @Override @NotNull ImmutableSeq<TermShape> args) implements Callable {
    public static @NotNull NameCall of(@NotNull String name) {
      return new NameCall(name, ImmutableSeq.empty());
    }
  }

  record ShapeCall(@NotNull CodeShape.MomentId id, @NotNull CodeShape shape,
                   @Override @NotNull ImmutableSeq<TermShape> args) implements Callable {}

  record CtorCall(@NotNull String dataRef, @NotNull CodeShape.MomentId ctorId,
                  @Override @NotNull ImmutableSeq<TermShape> args) implements Callable {}

  default @NotNull Named named(@NotNull String name) {
    return new Named(name, this);
  }
}