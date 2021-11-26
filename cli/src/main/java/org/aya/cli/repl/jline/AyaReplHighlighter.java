// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.jline;

import org.antlr.v4.runtime.Token;
import org.aya.distill.BaseDistiller;
import org.aya.parser.GeneratedLexerTokens;
import org.aya.pretty.doc.Doc;
import org.aya.repl.antlr.AntlrLexer;
import org.aya.repl.antlr.ReplHighlighter;
import org.jetbrains.annotations.NotNull;

public final class AyaReplHighlighter extends ReplHighlighter {
  public AyaReplHighlighter(@NotNull AntlrLexer lexer) {
    super(lexer);
  }

  @Override @NotNull protected Doc highlight(@NotNull Token t) {
    return GeneratedLexerTokens.KEYWORDS.containsKey(t.getType())
      ? Doc.styled(BaseDistiller.KEYWORD, t.getText())
      : Doc.plain(t.getText());
  }
}
