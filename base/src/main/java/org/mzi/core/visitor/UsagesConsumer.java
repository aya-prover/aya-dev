package org.mzi.core.visitor;

import asia.kala.EmptyTuple;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Var;
import org.mzi.core.term.AppTerm;
import org.mzi.core.term.RefTerm;

public final class UsagesConsumer implements TermConsumer<EmptyTuple> {
  public final @NotNull Var var;
  private int usageCount = 0;

  public UsagesConsumer(@NotNull Var var) {
    this.var = var;
  }

  @Contract(pure = true) public int usageCount() {
    return usageCount;
  }

  @Override public EmptyTuple visitRef(@NotNull RefTerm term, EmptyTuple emptyTuple) {
    if (var == term.var()) usageCount++;
    return emptyTuple;
  }

  @Override public EmptyTuple visitFnCall(AppTerm.@NotNull FnCall fnCall, EmptyTuple emptyTuple) {
    return emptyTuple;
  }
}
