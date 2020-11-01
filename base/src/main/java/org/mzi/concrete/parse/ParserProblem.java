package org.mzi.concrete.parse;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.Problem;
import org.mzi.api.error.SourcePos;

public record ParserProblem(@NotNull SourcePos sourcePos, @NotNull String message) implements Problem {
  @Override public @NotNull String describe() {
    return "Parser error at " + sourcePos + ": " + message + ".";
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
