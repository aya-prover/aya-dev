// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.parse;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.Reporter;
import org.mzi.api.error.SourcePos;

/**
 * An implementation of {@link BaseErrorListener} that reports syntax errors
 * from ANTLR to {@link org.mzi.api.error.Reporter}.
 */
public class ReporterErrorListener extends BaseErrorListener {
  private final @NotNull Reporter reporter;

  public ReporterErrorListener(@NotNull Reporter reporter) {
    this.reporter = reporter;
  }

  @Override
  public void syntaxError(Recognizer<?, ?> recognizer, Object o, int line, int pos, String msg, RecognitionException e) {
    Token offendingToken = ((Token) o);
    reporter.report(new ParseError(
      new SourcePos(
        offendingToken.getStartIndex(),
        offendingToken.getStopIndex(),
        line, pos, line, pos + offendingToken.getText().length()),
      msg));
  }
}
