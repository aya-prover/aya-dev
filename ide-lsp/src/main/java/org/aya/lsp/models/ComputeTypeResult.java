// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.models;

import org.aya.lsp.utils.LspRange;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.syntax.core.term.Term;
import org.aya.util.error.WithPos;
import org.javacs.lsp.Position;
import org.javacs.lsp.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;

/**
 * @param computed null if failed
 */
public record ComputeTypeResult(@NotNull URI uri, @Nullable String computed, @NotNull Range range) {
  public static class Params {
    public URI uri;
    public Position position;
  }

  public static @NotNull ComputeTypeResult bad(@NotNull Params params) {
    return new ComputeTypeResult(params.uri, null,
      new Range(params.position, params.position));
  }

  public static ComputeTypeResult good(@NotNull Params params, @NotNull WithPos<Term> type) {
    return new ComputeTypeResult(params.uri, type.data().toDoc(AyaPrettierOptions.informative()).debugRender(),
      LspRange.toRange(type.sourcePos()));
  }
}
