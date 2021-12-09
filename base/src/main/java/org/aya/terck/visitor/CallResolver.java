// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck.visitor;

import kala.collection.mutable.MutableSet;
import kala.tuple.Unit;
import org.aya.api.ref.DefVar;
import org.aya.core.def.Def;
import org.aya.core.def.FnDef;
import org.aya.core.term.CallTerm;
import org.aya.core.visitor.DefConsumer;
import org.aya.terck.CallGraph;
import org.aya.terck.CallMatrix;
import org.jetbrains.annotations.NotNull;

/**
 * Resolve calls and build call graph of recursive functions,
 * after {@link org.aya.tyck.StmtTycker}.
 *
 * @param targets only search calls to those definitions
 * @author kiva
 */
public record CallResolver(
  @NotNull FnDef caller,
  @NotNull MutableSet<Def> targets
) implements DefConsumer<CallGraph<Def>> {
  private void resolveCall(@NotNull CallTerm callTerm, CallGraph<Def> graph) {
    if (!(callTerm.ref() instanceof DefVar<?, ?> defVar)) return;
    var callee = ((Def) defVar.core);
    if (!targets.contains(callee)) return;
    var matrix = new CallMatrix<>(a -> a.telescope().size(), caller, callee);
    // TODO: set relations between caller parameters and callee arguments
    graph.put(matrix);
  }

  @Override public Unit visitFnCall(CallTerm.@NotNull Fn fnCall, CallGraph<Def> graph) {
    resolveCall(fnCall, graph);
    return DefConsumer.super.visitFnCall(fnCall, graph);
  }

  @Override public Unit visitConCall(CallTerm.@NotNull Con conCall, CallGraph<Def> graph) {
    resolveCall(conCall, graph);
    return DefConsumer.super.visitConCall(conCall, graph);
  }

  @Override public Unit visitDataCall(CallTerm.@NotNull Data dataCall, CallGraph<Def> graph) {
    resolveCall(dataCall, graph);
    return DefConsumer.super.visitDataCall(dataCall, graph);
  }

  @Override public Unit visitStructCall(CallTerm.@NotNull Struct structCall, CallGraph<Def> graph) {
    resolveCall(structCall, graph);
    return DefConsumer.super.visitStructCall(structCall, graph);
  }

  @Override public Unit visitAccess(CallTerm.@NotNull Access term, CallGraph<Def> defCallGraph) {
    resolveCall(term, defCallGraph);
    return DefConsumer.super.visitAccess(term, defCallGraph);
  }

  @Override public Unit visitPrimCall(@NotNull CallTerm.Prim prim, CallGraph<Def> graph) {
    resolveCall(prim, graph);
    return DefConsumer.super.visitPrimCall(prim, graph);
  }
}
