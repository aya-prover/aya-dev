package org.mzi.concrete.parse;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.Problem;
import org.mzi.api.error.SourcePos;
import org.mzi.pretty.doc.Doc;

public record ParserProblem(@NotNull SourcePos sourcePos, @NotNull String message) implements Problem {
  @Override public @NotNull Doc describe() {
    return Doc.plain("Parser error at " + sourcePos + ": " + message + ".");
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
