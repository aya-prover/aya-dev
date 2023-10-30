// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record FlclToken(
  @NotNull SourcePos range,
  @NotNull Type type
) {
  public enum Type {
    Keyword, Fn, Data, Number, Local
  }
}
