// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.jline;

import kala.collection.SeqView;
import org.antlr.v4.runtime.Token;
import org.aya.distill.BaseDistiller;
import org.aya.parser.GeneratedLexerTokens;
import org.aya.pretty.backend.string.StringPrinterConfig;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.LineReader;
import org.jline.reader.impl.DefaultHighlighter;
import org.jline.utils.AttributedString;

public class AyaReplHighlighter extends DefaultHighlighter {
  @Override
  public AttributedString highlight(LineReader reader, String buffer) {
    var tokens = AyaReplParser.tokensNoEOF(buffer);
    return AttributedString.fromAnsi(tokensToDoc(tokens)
      .renderToString(StringPrinterConfig.unixTerminal()));
  }

  private @NotNull Doc tokensToDoc(@NotNull SeqView<Token> tokens) {
    return Doc.cat(tokens.map(t -> GeneratedLexerTokens.KEYWORDS.containsKey(t.getType())
      ? Doc.styled(BaseDistiller.KEYWORD, t.getText())
      : Doc.plain(t.getText())));
  }
}
