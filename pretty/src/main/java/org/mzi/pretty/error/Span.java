// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.pretty.error;

import org.glavo.kala.Tuple;
import org.glavo.kala.Tuple2;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Iterator;

/**
 * @author kiva
 */
public record Span(
  @NotNull String input,
  int start,
  int end
) {
  public static @NotNull Span from(@NotNull String input, int start, int end) {
    if (start < 0 || end < 0
      || start >= input.length()
      || end >= input.length()
      || end < start) {
      throw new IndexOutOfBoundsException();
    }

    return new Span(input, start, end);
  }

  public Tuple2<@NotNull Position, @NotNull Position> split() {
    return Tuple.of(startPosition(), endPosition());
  }

  public @NotNull Position startPosition() {
    return new Position(input, start);
  }

  public @NotNull Position endPosition() {
    return new Position(input, end);
  }

  public Iterator<String> lines() {
    return Arrays.stream(toString().split("\n", -1)).iterator();
  }

  @Override public String toString() {
    return input.substring(start, end);
  }
}
