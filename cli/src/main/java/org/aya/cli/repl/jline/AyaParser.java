// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE file.
package org.aya.cli.repl.jline;

import org.antlr.v4.runtime.Token;
import org.aya.concrete.parse.AyaParsing;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.CompletingParsedLine;
import org.jline.reader.ParsedLine;
import org.jline.reader.Parser;
import org.jline.reader.SyntaxError;

import java.util.List;

public class AyaParser implements Parser {
  public record AyaParsedLine(
    int wordCursor, int rawWordCursor,
    @NotNull List<@NotNull String> words,
    @NotNull String word,
    int wordIndex,
    @NotNull String line,
    int cursor
  ) implements CompletingParsedLine {
    @Override public CharSequence escape(CharSequence charSequence, boolean b) {
      return charSequence;
    }

    @Override public int rawWordLength() {
      return words().size();
    }
  }

  @Override public ParsedLine parse(String line, int cursor, ParseContext context) throws SyntaxError {
    // Drop the EOF
    var tokens = AyaParsing.tokens(line).view().dropLast(1);
    var word = tokens.firstOption(token ->
      token.getStartIndex() >= cursor && token.getStopIndex() <= cursor
    ).getOrElse(tokens::last);
    // ^ In case we're in a whitespace or at the end
    var wordText = word.getText();
    return new AyaParsedLine(
      wordText.length(), word.getStartIndex() - cursor,
      tokens.stream().map(Token::getText).toList(),
      wordText, tokens.indexOf(word),
      line, cursor
    );
  }
}
