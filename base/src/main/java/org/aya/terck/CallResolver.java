// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import kala.collection.mutable.MutableSet;
import kala.tuple.Unit;
import kala.value.Ref;
import org.aya.api.ref.DefVar;
import org.aya.api.util.Arg;
import org.aya.core.Matching;
import org.aya.core.def.Def;
import org.aya.core.def.FnDef;
import org.aya.core.pat.Pat;
import org.aya.core.term.CallTerm;
import org.aya.core.term.ElimTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.DefConsumer;
import org.aya.util.error.SourcePos;
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
  @NotNull MutableSet<Def> targets,
  @NotNull Ref<Matching> currentMatching
) implements DefConsumer<CallGraph<Def>> {
  public CallResolver(@NotNull FnDef fn, @NotNull MutableSet<Def> targets) {
    this(fn, targets, new Ref<>());
  }

  private void resolveCall(@NotNull CallTerm callTerm, CallGraph<Def> graph) {
    if (!(callTerm.ref() instanceof DefVar<?, ?> defVar)) return;
    var callee = ((Def) defVar.core);
    if (!targets.contains(callee)) return;
    // TODO: source pos of the CallTerm?
    var matrix = CallMatrix.create(SourcePos.NONE,Def::telescope, caller, callee);
    fillMatrix(callTerm, callee, matrix);
    graph.put(matrix);
  }

  private void fillMatrix(@NotNull CallTerm callTerm, @NotNull Def callee, CallMatrix<Def> matrix) {
    // TODO: do not use zipView, which can only handle direct recursion
    caller.telescope.zipView(callee.telescope())
      .zipView(callTerm.args())
      .withIndex()
      .forEach(tup -> {
        var idx = tup._1;
        var callerParam = tup._2._1._1;
        var calleeParam = tup._2._1._2;
        var arg = tup._2._2;
        var relation = compare(idx, arg);
        matrix.set(callerParam, calleeParam, relation);
      });
  }

  private @NotNull Relation compare(int idx, @NotNull Arg<Term> arg) {
    var matching = currentMatching.value;
    if (matching == null) return Relation.Unknown;
    var pat = matching.patterns().getOrNull(idx);
    if (pat == null) return Relation.Unknown;
    return compare(arg.term(), pat);
  }

  /** foetus dependencies */
  private @NotNull Relation compare(@NotNull Term lhs, @NotNull Pat rhs) {
    if (rhs instanceof Pat.Ctor ctor) {
      // constructor elimination
      var subCompare = ctor.params().view().map(sub -> compare(lhs, sub));
      if (subCompare.anyMatch(r -> r != Relation.Unknown)) return Relation.LessThan;
    } else if (rhs instanceof Pat.Bind bind) {
      if (lhs instanceof RefTerm ref) {
        if (ref.var() == bind.bind()) return Relation.Equal;
        return Relation.Unknown;
      }
      // application and projection
      var head = headOf(lhs);
      if (head instanceof RefTerm ref && bind.bind() == ref.var()) return Relation.LessThan;
    }
    return Relation.Unknown;
  }

  private @NotNull Term headOf(@NotNull Term term) {
    return switch (term) {
      case ElimTerm.App app -> headOf(app.of());
      case ElimTerm.Proj proj -> headOf(proj.of());
      case CallTerm.Access access -> headOf(access.of());
      default -> term;
    };
  }

  @Override public void visitMatching(@NotNull Matching matching, CallGraph<Def> graph) {
    this.currentMatching.value = matching;
    DefConsumer.super.visitMatching(matching, graph);
    this.currentMatching.value = null;
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
