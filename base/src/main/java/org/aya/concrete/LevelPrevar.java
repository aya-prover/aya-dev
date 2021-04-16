// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete;

import org.aya.api.ref.Var;
import org.aya.core.sort.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @param level null if unsolved
 * @author ice1000
 */
public record LevelPrevar(
  @NotNull String name,
  @NotNull Kind kind,
  @Nullable Level level
) implements Var {
  public LevelPrevar(@NotNull String name, @NotNull Kind kind) {
    this(name, kind, null);
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
