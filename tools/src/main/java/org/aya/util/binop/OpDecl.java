// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.binop;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface OpDecl {
  enum BindPred {
    Tighter("tighter"),
    Looser("looser");

    public final @NotNull String keyword;

    BindPred(@NotNull String keyword) {
      this.keyword = keyword;
    }
  }

  @Nullable OpInfo opInfo();

  record OpInfo(@NotNull String name, @NotNull Assoc assoc, int argc) {
  }

  @NotNull OpDecl APPLICATION = () -> new OpInfo("application", Assoc.InfixL, 2);
}
