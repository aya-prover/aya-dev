package org.mzi.core.visitor;

import asia.kala.Unit;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Var;
import org.mzi.core.term.AppTerm;
import org.mzi.core.term.HoleTerm;
import org.mzi.core.term.RefTerm;

/**
 * @author ice1000
 */
public interface VarConsumer extends TermConsumer<Unit> {
  default Unit visitRef(@NotNull RefTerm term, Unit emptyTuple) {
    visitVar(term.var());
    return emptyTuple;
  }

  default Unit visitHole(@NotNull HoleTerm term, Unit emptyTuple) {
    visitVar(term.var());
    return emptyTuple;
  }

  default Unit visitFnCall(AppTerm.@NotNull FnCall fnCall, Unit emptyTuple) {
    visitVar(fnCall.fnRef());
    return emptyTuple;
  }

  @Contract(mutates = "this") void visitVar(Var usage);
}
