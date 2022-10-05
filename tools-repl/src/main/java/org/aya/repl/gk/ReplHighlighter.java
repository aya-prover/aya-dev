// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.repl.gk;

import org.aya.pretty.backend.string.StringPrinterConfig;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.LineReader;
import org.jline.reader.impl.DefaultHighlighter;
import org.jline.utils.AttributedString;

public abstract class ReplHighlighter extends DefaultHighlighter {
  protected final @NotNull JFlexAdapter lexer;

  public ReplHighlighter(@NotNull JFlexAdapter lexer) {
    this.lexer = lexer;
  }

  protected abstract @NotNull Doc highlight(@NotNull JFlexAdapter.Token t);

  @Override
  public AttributedString highlight(LineReader reader, String buffer) {
    var tokens = lexer.tokensNoEOF(buffer);

    return AttributedString.fromAnsi(Doc.cat(tokens.map(this::highlight))
      .renderToString(StringPrinterConfig.unixTerminal()));
  }
}
