// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.position;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public interface SourceNode {
  @Contract(pure = true) @NotNull SourcePos sourcePos();
}
