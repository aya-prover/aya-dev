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
      return word.length();
    }
  }

  /*
  private ParsedLine debug(String line, int cursor, ImmutableSeq<Token> tokens, AyaParsedLine parsed) {
    try {
      var builtin = (CompletingParsedLine) new DefaultParser().parse(line, cursor, null);
      var path = Paths.get("debug.txt").toAbsolutePath();
      Files.writeString(path, tokens.map(tok -> tok.getStartIndex() + ":" + tok.getStopIndex()) +
          "\nLine: [" + line + "], cur: " + cursor + "\n" +
          "word:"          + parsed.word +            ":" + builtin.word() + "\n" +
          "wordCursor:"    + parsed.wordCursor +      ":" + builtin.wordCursor() + "\n" +
          "rawWordCursor:" + parsed.rawWordCursor() + ":" + builtin.rawWordCursor() + "\n" +
          "words:"         + parsed.words +           ":" + builtin.words() + "\n" +
          "rawWordLength:" + parsed.rawWordLength() + ":" + builtin.rawWordLength() + "\n" +
          "wordIndex:"     + parsed.wordIndex +       ":" + builtin.wordIndex() + "\n\n",
        StandardOpenOption.APPEND, StandardOpenOption.CREATE);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return parsed;
  }
  */

  @Override public ParsedLine parse(String line, int cursor, ParseContext context) throws SyntaxError {
    if (line.isBlank()) return new AyaParsedLine(0, Collections.emptyList(), "", 0, line, cursor);
    // Drop the EOF
    var tokens = AyaParsing.tokens(line).view()
      .dropLast(1)
      .filter(token -> token.getChannel() != Token.HIDDEN_CHANNEL)
      .toImmutableSeq();
    var wordOpt = tokens.firstOption(token ->
      token.getStartIndex() <= cursor && token.getStopIndex() + 1 >= cursor
    );
    // In case we're in a whitespace or at the end
    if (wordOpt.isEmpty()) {
      var token = tokens.firstOption(tok -> tok.getStartIndex() >= cursor)
        .getOrElse(tokens::last);
      var wordCursor = cursor - token.getStartIndex();
      return new AyaParsedLine(
        Math.max(wordCursor, 0), tokens.stream().map(Token::getText).toList(),
        token.getText(), tokens.size() - 1, line, cursor
      );
    }
    var word = wordOpt.get();
    var wordText = word.getText();
    return new AyaParsedLine(
      cursor - word.getStartIndex(),
      tokens.stream().map(Token::getText).toList(),
      wordText, tokens.indexOf(word), line, cursor
    );
  }
}
