package org.mzi.core.visitor;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Var;

public final class UsagesConsumer implements VarConsumer {
  public final @NotNull Var var;
  private int usageCount = 0;

  @Contract(pure = true) public UsagesConsumer(@NotNull Var var) {
    this.var = var;
  }

  @Contract(pure = true) public int usageCount() {
    return usageCount;
  }

  @Contract(mutates = "this") @Override public void visitVar(Var usage) {
    if (var == usage) usageCount++;
  }
}
