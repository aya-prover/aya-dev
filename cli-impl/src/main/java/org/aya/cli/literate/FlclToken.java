// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

public record FlclToken(
  @NotNull TextRange range,
  @NotNull Type type
) {
  public enum Type {
    Keyword, Fn, Data, Number
  }
}
