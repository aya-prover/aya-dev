// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.visitor;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Var;

public final class UsageCounter implements VarConsumer {
  public final @NotNull Var var;
  private int usageCount = 0;

  @Contract(pure = true) public UsageCounter(@NotNull Var var) {
    this.var = var;
  }

  @Contract(pure = true) public int usageCount() {
    return usageCount;
  }

  @Contract(mutates = "this") @Override public void visitVar(Var usage) {
    if (var == usage) usageCount++;
  }
}
