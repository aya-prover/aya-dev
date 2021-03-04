// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli;

import org.jetbrains.annotations.NotNull;

public record CompilerFlags(
  @NotNull String successNotion,
  @NotNull String failNotion
) {
  public static final CompilerFlags DEFAULT_FLAGS = new CompilerFlags("\uD83D\uDC02\uD83C\uDF7A", "\uD83D\uDD28");
  public static final CompilerFlags ASCII_FLAGS = new CompilerFlags("That looks right!", "What are you doing?");
}
