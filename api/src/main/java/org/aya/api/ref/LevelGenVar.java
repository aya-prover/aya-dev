// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.ref;

import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 * @apiNote used only in concrete syntax.
 */
public record LevelGenVar(@NotNull Kind kind, @NotNull String name) implements Var {
  public enum Kind {
    Universe("ulevel", 0),
    Homotopy("hlevel", 2);

    public final @NotNull String keyword;
    public final int defaultValue;

    Kind(@NotNull String keyword, int defaultValue) {
      this.keyword = keyword;
      this.defaultValue = defaultValue;
    }
  }
}
