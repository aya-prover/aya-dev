package org.mzi.api.error;

import asia.kala.collection.mutable.ArrayBuffer;
import asia.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record CollectReporter(@NotNull Buffer<@NotNull Error> errors) implements Reporter {
  public CollectReporter() {
    this(new ArrayBuffer<>());
  }

  @Override public void report(@NotNull Error error) {
    errors.append(error);
  }
}
