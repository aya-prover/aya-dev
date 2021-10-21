// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record ExecutionResultText(@Nullable String text, @Nullable String errText) {
  public static @NotNull ExecutionResultText successful(@Nullable String text) {
    return new ExecutionResultText(text, null);
  }

  public static @NotNull ExecutionResultText failed(@Nullable String errText) {
    return new ExecutionResultText(null, errText);
  }
}
