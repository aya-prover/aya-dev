// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.jetbrains.annotations.NotNull;

public record InOutTerm(@NotNull Term phi, @NotNull Term u, @NotNull Kind kind) implements Term {
  public enum Kind {
    In, Out;
    public final @NotNull String fnName = name().toLowerCase() + "S";
  }
}
