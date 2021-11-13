// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.models;

import org.aya.api.distill.DistillerOptions;
import org.aya.core.term.Term;
import org.aya.lsp.utils.LspRange;
import org.aya.util.error.WithPos;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;

public record ComputeTermResult(@NotNull String uri, @NotNull String computed, @NotNull Range range) {
  public static class Params {
    public String uri;
    public Position position;
  }

  public static @NotNull ComputeTermResult bad(@NotNull Params params) {
    return new ComputeTermResult(params.uri, "<unknown>",
      new Range(params.position, params.position));
  }

  public static ComputeTermResult good(@NotNull Params params, @NotNull WithPos<Term> type) {
    return new ComputeTermResult(params.uri, type.data().toDoc(DistillerOptions.informative()).debugRender(),
      LspRange.toRange(type.sourcePos()));
  }
}
