// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp.models;

import org.aya.api.util.WithPos;
import org.aya.core.term.Term;
import org.aya.lsp.utils.LspRange;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;

public record ComputeTypeResult(
  @NotNull String uri,
  @NotNull String computedType,
  @NotNull Range range
) {
  public static record Params(
    @NotNull String uri,
    @NotNull Position position
  ) {
  }

  public static @NotNull ComputeTypeResult bad(@NotNull Params params) {
    return new ComputeTypeResult(params.uri, "<unknown>",
      new Range(params.position, params.position));
  }

  public static ComputeTypeResult good(@NotNull Params params, @NotNull WithPos<Term> type) {
    return new ComputeTypeResult(params.uri, type.data().toDoc().renderToHtml(),
      LspRange.toRange(type.sourcePos()));
  }
}
