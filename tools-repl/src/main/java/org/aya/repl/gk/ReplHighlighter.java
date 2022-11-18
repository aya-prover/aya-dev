// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.repl.gk;

import com.intellij.lexer.FlexLexer;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.LineReader;
import org.jline.reader.impl.DefaultHighlighter;
import org.jline.utils.AttributedString;

public abstract class ReplHighlighter extends DefaultHighlighter {
  protected final @NotNull FlexLexer lexer;

  public ReplHighlighter(@NotNull FlexLexer lexer) {
    this.lexer = lexer;
  }

  protected abstract @NotNull Doc highlight(String text, @NotNull FlexLexer.Token t);

  @Override public AttributedString highlight(LineReader reader, String buffer) {
    lexer.reset(buffer, 0, buffer.length(), 0);
    var tokens = lexer.allTheWayDown();
    return AttributedString.fromAnsi(Doc.cat(tokens.map(t -> highlight(t.range().substring(buffer), t)))
      .renderToTerminal());
  }
}
