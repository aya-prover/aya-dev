// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.binop;

import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public interface SourceNode {
  @NotNull SourcePos sourcePos();
}
