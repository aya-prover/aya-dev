// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Option;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.cli.literate.SyntaxHighlight;
import org.aya.lsp.models.HighlightResult;
import org.aya.lsp.utils.LspRange;
import org.jetbrains.annotations.NotNull;

public interface SemanticHighlight {
  static @NotNull ImmutableSeq<HighlightResult> invoke(@NotNull LibraryOwner owner) {
    var symbols = MutableList.<HighlightResult>create();
    highlight(owner, symbols);
    return symbols.toImmutableSeq();
  }

  private static void highlight(@NotNull LibraryOwner owner, @NotNull MutableList<HighlightResult> result) {
    owner.librarySources().forEach(src -> result.append(highlightOne(src)));
    owner.libraryDeps().forEach(dep -> highlight(dep, result));
  }

  private static @NotNull HighlightResult highlightOne(@NotNull LibrarySource source) {
    var symbols = MutableList.<HighlightResult.Symbol>create();
    var program = source.program().get();
    if (program != null) {
      SyntaxHighlight
        .highlight(null, Option.none(), program).view()
        .flatMap(HighlightResult.Symbol::from)
        .forEach(symbols::append);
    }
    return new HighlightResult(
      source.underlyingFile().toUri(),
      symbols.filter(t -> t.range() != LspRange.NONE));
  }
}
