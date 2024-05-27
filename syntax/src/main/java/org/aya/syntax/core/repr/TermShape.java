// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.repr;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.term.SortKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author kiva
 */
sealed public interface TermShape {
  enum Any implements TermShape { INSTANCE }

  /**
   * The shape to Sort term, I am not very work well at type theory, so improve this feel free!
   *
   * @param kind  the SortKind, null if accept any kind of sort. see {@code ShapeMatcher.matchTerm}
   * @param ulift the lower bound of the type level.
   * @author hoshino
   */
  record Sort(@Nullable SortKind kind, int ulift) implements TermShape { }
  record DeBruijn(int index) implements TermShape { }

  sealed interface Callable extends TermShape {
    @NotNull ImmutableSeq<TermShape> args();
  }

  record NameCall(
    @NotNull CodeShape.MomentId name,
    @Override @NotNull ImmutableSeq<TermShape> args
  ) implements Callable {
    public static @NotNull NameCall of(@NotNull CodeShape.MomentId name, @NotNull TermShape... args) {
      return new NameCall(name, ImmutableSeq.from(args));
    }
  }

  record ShapeCall(
    @NotNull CodeShape.MomentId name, @NotNull CodeShape shape,
    @Override @NotNull ImmutableSeq<TermShape> args
  ) implements Callable, CodeShape.Moment {
    public static @NotNull ShapeCall of(@NotNull CodeShape.MomentId name, @NotNull CodeShape shape, @NotNull TermShape... args) {
      return new ShapeCall(name, shape, ImmutableSeq.from(args));
    }
  }

  record ConCall(
    @NotNull CodeShape.MomentId dataId, @NotNull CodeShape.GlobalId conId,
    @Override @NotNull ImmutableSeq<TermShape> args
  ) implements Callable {
    public static @NotNull ConCall of(@NotNull CodeShape.MomentId dataId, @NotNull CodeShape.GlobalId conId, @NotNull TermShape... args) {
      return new ConCall(dataId, conId, ImmutableSeq.from(args));
    }
  }
}
