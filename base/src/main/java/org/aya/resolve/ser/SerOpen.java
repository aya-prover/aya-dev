// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.ser;

import org.aya.syntax.concrete.stmt.ModuleName;
import org.jetbrains.annotations.NotNull;

public record SerOpen(
  @NotNull boolean reExport,
  @NotNull ModuleName.Qualified path,
  @NotNull SerUseHide useHide
) implements SerCommand {
}
