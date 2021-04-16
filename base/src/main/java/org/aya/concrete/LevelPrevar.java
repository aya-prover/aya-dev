// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete;

import org.aya.api.ref.Var;
import org.aya.util.Constants;
import org.glavo.kala.control.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 'Prevar' means it is not yet a valid level var. It will be elaborated into
 * {@link org.aya.core.sort.LevelVar}.
 *
 * @param knownValue null if polymorphic or in the ulevel or hlevel definitions and -1 if infinity.
 * @author ice1000
 */
public record LevelPrevar(
  @NotNull String name,
  @NotNull Kind kind,
  @Nullable Either<Integer, LevelPrevar> knownValue
) implements Var {
  public LevelPrevar(@NotNull String name, @NotNull Kind kind) {
    this(name, kind, null);
  }

  public static @NotNull LevelPrevar make(int level, Kind kind) {
    return new LevelPrevar(Constants.ANONYMOUS_PREFIX, kind, Either.left(level));
  }

  public enum Kind {
    Universe("ulevel"),
    Homotopy("hlevel");

    public final @NotNull String keyword;

    Kind(@NotNull String keyword) {
      this.keyword = keyword;
    }
  }
}
