// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp.models;

import org.jetbrains.annotations.NotNull;

public record ComputeTypeResult(
  @NotNull String uri,
  @NotNull String computedType,
  int startLine, int startCol,
  int endLine, int endCol
) {
  public static record Params(
    @NotNull String uri,
    int line, int col
  ) {
  }

  public static @NotNull ComputeTypeResult bad(@NotNull Params params) {
    return new ComputeTypeResult(params.uri, "<unknown>",
      params.line, params.col, params.line, params.col);
  }
}
