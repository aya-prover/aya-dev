// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.error;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public interface SourceNode {
  @Contract(pure = true) @NotNull SourcePos sourcePos();
}
