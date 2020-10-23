package org.mzi.core.visitor;

import asia.kala.EmptyTuple;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Var;
import org.mzi.core.term.AppTerm;
import org.mzi.core.term.HoleTerm;
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

  @Contract(mutates = "this") private void visitVar(Var usage) {
    if (var == usage) usageCount++;
  }

  @Override public EmptyTuple visitRef(@NotNull RefTerm term, EmptyTuple emptyTuple) {
    visitVar(term.var());
    return emptyTuple;
  }

  @Override public EmptyTuple visitHole(@NotNull HoleTerm holeTerm, EmptyTuple emptyTuple) {
    visitVar(holeTerm.var());
    return emptyTuple;
  }

  @Override public EmptyTuple visitFnCall(AppTerm.@NotNull FnCall fnCall, EmptyTuple emptyTuple) {
    visitVar(fnCall.fnRef());
    return emptyTuple;
  }
}
