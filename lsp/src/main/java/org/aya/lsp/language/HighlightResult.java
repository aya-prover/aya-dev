// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp.language;

import org.aya.lsp.highlight.Symbol;
import kala.collection.SeqLike;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public record HighlightResult(
  @NotNull String uri,
  @NotNull List<Symbol> symbols
) {
  public HighlightResult(@NotNull String uri, @NotNull SeqLike<Symbol> symbols) {
    this(uri, symbols.collect(Collectors.toList()));
  }
}
