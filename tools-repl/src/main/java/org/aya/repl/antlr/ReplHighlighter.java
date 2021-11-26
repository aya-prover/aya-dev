// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.repl.antlr;

import org.antlr.v4.runtime.Token;
import org.aya.pretty.backend.string.StringPrinterConfig;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.LineReader;
import org.jline.reader.impl.DefaultHighlighter;
import org.jline.utils.AttributedString;

public abstract class ReplHighlighter extends DefaultHighlighter {
  protected final @NotNull AntlrLexer lexer;

  public ReplHighlighter(@NotNull AntlrLexer lexer) {
    this.lexer = lexer;
  }

  protected abstract @NotNull Doc highlight(@NotNull Token t);

  @Override
  public AttributedString highlight(LineReader reader, String buffer) {
    var tokens = lexer.tokensNoEOF(buffer);
    return AttributedString.fromAnsi(Doc.cat(tokens.map(this::highlight))
      .renderToString(StringPrinterConfig.unixTerminal()));
  }
}
