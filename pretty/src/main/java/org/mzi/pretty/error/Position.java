// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.pretty.error;

import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
public record Position(
  @NotNull String sourceCode,
  int pos
) {

  public static @NotNull Position withPos(@NotNull String input, int pos) {
    if (pos < 0 || pos >= input.length()) {
      throw new IndexOutOfBoundsException();
    }
    return new Position(input, pos);
  }

  public static @NotNull Position fromStart(@NotNull String input) {
    return new Position(input, 0);
  }

  public @NotNull Span span(@NotNull Position other) {
    sameInput(other);
    return new Span(sourceCode, this.pos, other.pos);
  }

  public boolean atStart() {
    return pos == 0;
  }

  public boolean atEnd() {
    return pos == sourceCode.length();
  }

  private void sameInput(@NotNull Position other) {
    if (!this.sourceCode.equals(other.sourceCode)) {
      throw new IllegalArgumentException();
    }
  }
}
