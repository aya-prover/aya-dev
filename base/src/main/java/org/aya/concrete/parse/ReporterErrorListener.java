// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.parse;

import org.antlr.v4.runtime.*;
import org.aya.api.error.Reporter;
import org.aya.concrete.parse.error.ParseError;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

/**
 * An implementation of {@link BaseErrorListener} that reports syntax errors
 * from ANTLR to {@link Reporter}.
 */
public class ReporterErrorListener extends BaseErrorListener {
  private final @NotNull Reporter reporter;
  private final @NotNull SourceFile sourceFile;

  public ReporterErrorListener(@NotNull SourceFile sourceFile, @NotNull Reporter reporter) {
    this.sourceFile = sourceFile;
    this.reporter = reporter;
  }

  @Override
  public void syntaxError(Recognizer<?, ?> recognizer, Object o, int line, int pos, String msg, RecognitionException e) {
    Token offendingToken = ((Token) o);
    if (offendingToken == null) {
      // TODO[kiva]: it seems that LexerNoViableAltException is the only lexer error
      lexerError(line, pos, msg, (LexerNoViableAltException) e);
    } else {
      parserError(line, pos, msg, offendingToken);
    }
    throw new ParsingInterruptedException();
  }

  private void parserError(int line, int pos, String msg, Token offendingToken) {
    int start = offendingToken.getStartIndex();
    int end = offendingToken.getStopIndex();
    if (offendingToken.getType() == Token.EOF) {
      // see https://github.com/ice1000/aya-prover/issues/165#issuecomment-786533906
      start = end = SourcePos.UNAVAILABLE_AND_FUCK_ANTLR4;
    }

    reporter.report(new ParseError(new SourcePos(
      sourceFile, start, end, line, pos, line,
      pos + offendingToken.getText().length()), msg));
  }

  private void lexerError(int line, int pos, String msg, LexerNoViableAltException e) {
    reporter.report(new ParseError(
      new SourcePos(
        sourceFile,
        e.getStartIndex(),
        e.getInputStream().index(),
        line, pos, line, pos),
      msg));
  }
}
