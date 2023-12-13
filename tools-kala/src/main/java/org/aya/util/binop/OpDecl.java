// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.binop;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface OpDecl {
  enum BindPred {
    Tighter, Looser
  }

  @Contract(pure = true) @Nullable OpInfo opInfo();

  record OpInfo(@NotNull String name, @NotNull Assoc assoc) {
  }

  @NotNull OpDecl APPLICATION = () -> new OpInfo("application", Assoc.InfixL);
}
