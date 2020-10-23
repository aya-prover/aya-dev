package org.mzi.core.visitor;

import asia.kala.EmptyTuple;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Var;
import org.mzi.core.term.AppTerm;
import org.mzi.core.term.HoleTerm;
import org.mzi.core.term.RefTerm;

/**
 * @author ice1000
 */
public interface VarConsumer extends TermConsumer<EmptyTuple> {
  default EmptyTuple visitRef(@NotNull RefTerm term, EmptyTuple emptyTuple) {
    visitVar(term.var());
    return emptyTuple;
  }

  default EmptyTuple visitHole(@NotNull HoleTerm holeTerm, EmptyTuple emptyTuple) {
    visitVar(holeTerm.var());
    return emptyTuple;
  }

  default EmptyTuple visitFnCall(AppTerm.@NotNull FnCall fnCall, EmptyTuple emptyTuple) {
    visitVar(fnCall.fnRef());
    return emptyTuple;
  }

  @Contract(mutates = "this") void visitVar(Var usage);
}
