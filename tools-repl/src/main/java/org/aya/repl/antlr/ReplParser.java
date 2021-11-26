// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.repl.antlr;

import org.antlr.v4.runtime.Token;
import org.aya.repl.Command;
import org.aya.repl.CommandArg;
import org.aya.repl.CommandManager;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;

import java.util.Collections;
import java.util.List;

/**
 * @param shellLike see {@link org.aya.repl.CommandArg#shellLike}
 */
public record ReplParser(
  @NotNull CommandManager cmd, @NotNull AntlrLexer lexer,
  @NotNull DefaultParser shellLike
) implements Parser {
  public ReplParser(@NotNull CommandManager cmd, @NotNull AntlrLexer lexer) {
    this(cmd, lexer, new DefaultParser());
  }

  public record ReplParsedLine(
    @Override int wordCursor,
    @Override @NotNull List<@NotNull String> words,
    @Override @NotNull String word,
    @Override int wordIndex,
    @Override @NotNull String line,
    @Override int cursor
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

  @Override public ParsedLine parse(String line, int cursor, ParseContext context) throws SyntaxError {
    if (line.isBlank()) return simplest(line, cursor, 0, Collections.emptyList());
    // ref: https://github.com/jline/jline3/issues/36
    if ((context == ParseContext.UNSPECIFIED || context == ParseContext.ACCEPT_LINE)
      && line.startsWith(Command.MULTILINE_BEGIN) && !line.endsWith(Command.MULTILINE_END)) {
      throw new EOFError(-1, cursor, "In multiline mode");
    }
    var trim = line.trim();
    if (trim.startsWith(Command.PREFIX)) {
      var shellAlike = cmd.parse(trim.substring(1)).command().view()
        .mapNotNull(CommandManager.CommandGen::argFactory)
        .anyMatch(CommandArg::shellLike);
      // ^ if anything matches
      if (shellAlike) return shellLike.parse(line, cursor, context);
    }
    // Drop whitespaces
    var tokens = lexer.tokensNoEOF(line)
      .filter(token -> token.getChannel() != Token.HIDDEN_CHANNEL)
      .toImmutableSeq();
    var wordOpt = tokens.firstOption(token ->
      token.getStartIndex() <= cursor && token.getStopIndex() + 1 >= cursor
    );
    // In case we're in a whitespace or at the end
    if (wordOpt.isEmpty()) {
      var tokenOpt = tokens.firstOption(tok -> tok.getStartIndex() >= cursor);
      if (tokenOpt.isEmpty())
        return simplest(line, cursor, tokens.size(), tokens.stream().map(Token::getText).toList());
      var token = tokenOpt.get();
      var wordCursor = cursor - token.getStartIndex();
      return new ReplParsedLine(
        Math.max(wordCursor, 0), tokens.stream().map(Token::getText).toList(),
        token.getText(), tokens.size() - 1, line, cursor
      );
    }
    var word = wordOpt.get();
    var wordText = word.getText();
    return new ReplParsedLine(
      cursor - word.getStartIndex(),
      tokens.stream().map(Token::getText).toList(),
      wordText, tokens.indexOf(word), line, cursor
    );
  }

  public @NotNull ReplParsedLine simplest(String line, int cursor, int wordIndex, List<@NotNull String> tokens) {
    return new ReplParsedLine(0, tokens, "", wordIndex, line, cursor);
  }
}
