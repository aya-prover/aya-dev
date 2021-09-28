// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.stmt;

import org.aya.api.util.Assoc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface OpDecl {
  @Nullable Operator asOperator();

  @NotNull String APP_NAME = "application";
  @NotNull OpDecl APP = () -> new Operator(APP_NAME, Assoc.Infix);

  record Operator(@Nullable String name, @NotNull Assoc assoc) {
  }
}
