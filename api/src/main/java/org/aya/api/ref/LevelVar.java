// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.ref;

import org.glavo.kala.value.Ref;
import org.jetbrains.annotations.NotNull;

public record LevelVar<Level>(
  @NotNull String name,
  @NotNull Kind kind,
  @NotNull Ref<@NotNull Level> level
) implements Var {
  public enum Kind {
    Universe,
    Homotopy
  }
}
