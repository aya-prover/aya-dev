// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.error;

import org.jetbrains.annotations.NotNull;

public interface Span {
  @NotNull String input();

  @NotNull Span.Data normalize(PrettyErrorConfig config);

  record Data(
    int startLine,
    int startCol,
    int endLine,
    int endCol
  ) {
    public @NotNull Data union(@NotNull Data other) {
      return new Data(
        Math.min(startLine, other.startLine),
        Math.max(startCol, other.startCol),
        Math.max(endLine, other.endLine),
        Math.max(endCol, other.endCol)
      );
    }
  }
}
