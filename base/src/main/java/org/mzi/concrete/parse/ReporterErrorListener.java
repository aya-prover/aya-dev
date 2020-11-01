package org.mzi.concrete.parse;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
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
    reporter.report(new ParserProblem(new SourcePos(-1, -1, line, pos, line, pos + 1), msg));
  }
}
