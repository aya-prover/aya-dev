// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE file.
package org.aya.cli.repl.jline;

import org.antlr.v4.runtime.Token;
import org.aya.concrete.parse.AyaParsing;
import org.aya.parser.AyaLexer;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.CompletingParsedLine;
import org.jline.reader.ParsedLine;
import org.jline.reader.Parser;
import org.jline.reader.SyntaxError;
import org.jline.reader.impl.DefaultParser;

import java.util.Collections;
import java.util.List;

public class AyaParser implements Parser {
  public record AyaParsedLine(
    int wordCursor,
    @NotNull List<@NotNull String> words,
    @NotNull String word,
    int wordIndex,
    @NotNull String line,
    int cursor
  ) implements CompletingParsedLine {
    @Override public CharSequence escape(CharSequence charSequence, boolean b) {
      return charSequence;
    }

    @Override public int rawWordCursor() {
      return wordCursor;
    }

    @Override public int rawWordLength() {
      return words().size();
    }
  }

  @Override public ParsedLine parse(String line, int cursor, ParseContext context) throws SyntaxError {
    if (line.isBlank()) return new AyaParsedLine(0, Collections.emptyList(), "", 0, line, cursor);
    var builtin = (CompletingParsedLine) new DefaultParser().parse(line, cursor, context);
    // Drop the EOF
    var tokens = AyaParsing.tokens(line).view()
      .dropLast(1)
      .filter(token -> token.getChannel() != AyaLexer.HIDDEN)
      .toImmutableSeq();
    var wordOpt = tokens.firstOption(token ->
      token.getStartIndex() >= cursor && token.getStopIndex() <= cursor
    );
    if (wordOpt.isEmpty()) return new AyaParsedLine(
      0, tokens.stream().map(Token::getText).toList(),
      tokens.last().getText(), tokens.size() - 1, line, cursor
    );
    var word = wordOpt.get();
    // ^ In case we're in a whitespace or at the end
    var wordText = word.getText();
    var parsed = new AyaParsedLine(
      cursor - word.getStartIndex(),
      tokens.stream().map(Token::getText).toList(),
      wordText, tokens.indexOf(word),
      line, cursor
    );
    return parsed;
  }
}
