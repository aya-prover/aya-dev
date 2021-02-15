// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.cli;

import org.jetbrains.annotations.NotNull;

public record CompilerFlags(
  @NotNull String successNotion,
  @NotNull String failNotion
) {
  public static CompilerFlags defaultFlags() {
    return new CompilerFlags("\uD83D\uDC02\uD83C\uDF7A", "\uD83D\uDD28");
  }

  public static CompilerFlags asciiOnlyFlags() {
    return new CompilerFlags("That looks right!", "What are you doing?");
  }
}
