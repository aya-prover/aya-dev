// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import org.aya.api.ref.Var;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public final class UsageCounter implements VarConsumer<Unit> {
  public final @NotNull Var var;
  private int usageCount = 0;

  @Contract(pure = true) public UsageCounter(@NotNull Var var) {
    this.var = var;
  }

  @Contract(pure = true) public int usageCount() {
    return usageCount;
  }

  @Contract(mutates = "this") @Override public void visitVar(Var usage, Unit unit) {
    if (var == usage) usageCount++;
  }
}
