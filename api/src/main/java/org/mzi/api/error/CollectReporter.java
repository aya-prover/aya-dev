package org.mzi.api.error;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record CollectReporter(@NotNull List<@NotNull Error> errors) implements Reporter {
  public CollectReporter() {
    this(new ArrayList<>());
  }

  @Override public void report(@NotNull Error error) {
    errors.add(error);
  }
}
