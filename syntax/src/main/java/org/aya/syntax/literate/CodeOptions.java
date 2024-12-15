// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.literate;

import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;

public record CodeOptions(
  @NotNull NormalizeMode mode,
  @NotNull PrettierOptions options,
  @NotNull ShowCode showCode
) {
  public enum ShowCode {
    Concrete, Core, Type
  }
  public enum NormalizeMode {
    HEAD, FULL, NULL
  }
}
