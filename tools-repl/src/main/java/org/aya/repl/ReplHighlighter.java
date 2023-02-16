// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.repl;

import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.LineReader;
import org.jline.reader.impl.DefaultHighlighter;
import org.jline.utils.AttributedString;

public abstract class ReplHighlighter<T> extends DefaultHighlighter {
  protected final @NotNull ReplLexer<T> lexer;

  public ReplHighlighter(@NotNull ReplLexer<T> lexer) {
    this.lexer = lexer;
  }

  protected abstract @NotNull Doc highlight(String text, @NotNull T t);
  protected abstract @NotNull String renderToTerminal(@NotNull Doc doc);

  @Override public AttributedString highlight(LineReader reader, String buffer) {
    lexer.reset(buffer, 0);
    var tokens = lexer.allTheWayDown();
    var doc = Doc.cat(tokens.map(t -> highlight(lexer.tokenText(buffer, t), t)));
    return AttributedString.fromAnsi(renderToTerminal(doc));
  }
}
