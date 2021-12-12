// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import kala.collection.mutable.MutableSet;
import kala.tuple.Unit;
import kala.value.Ref;
import org.aya.api.ref.DefVar;
import org.aya.core.Matching;
import org.aya.core.def.Def;
import org.aya.core.def.FnDef;
import org.aya.core.pat.Pat;
import org.aya.core.term.CallTerm;
import org.aya.core.term.ElimTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.DefConsumer;
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
) implements DefConsumer<CallGraph<Def, Term.Param>> {
  public CallResolver(@NotNull FnDef fn, @NotNull MutableSet<Def> targets) {
    this(fn, targets, new Ref<>());
  }

  private void resolveCall(@NotNull CallTerm callTerm, CallGraph<Def, Term.Param> graph) {
    if (!(callTerm.ref() instanceof DefVar<?, ?> defVar)) return;
    var callee = ((Def) defVar.core);
    if (!targets.contains(callee)) return;
    // TODO: reduce arguments? I guess no. see https://github.com/agda/agda/issues/2403
    var matrix = new CallMatrix<>(callTerm, caller, callee, caller.telescope, callee.telescope());
    fillMatrix(callTerm, callee, matrix);
    graph.put(matrix);
  }

  private void fillMatrix(@NotNull CallTerm callTerm, @NotNull Def callee, CallMatrix<Def, Term.Param> matrix) {
    var matching = currentMatching.value;
    assert matching != null;
    for (var domThing : matching.patterns().zipView(caller.telescope)) {
      for (var codomThing : callTerm.args().zipView(callee.telescope())) {
        var relation = compare(codomThing._1.term(), domThing._1);
        matrix.set(domThing._2, codomThing._2, relation);
      }
    }
  }

  /** foetus dependencies */
  private @NotNull Relation compare(@NotNull Term lhs, @NotNull Pat rhs) {
    if (rhs instanceof Pat.Ctor ctor) {
      if (lhs instanceof CallTerm.Con con) {
        if (con.ref() != ctor.ref()) return Relation.Unknown;
        if (ctor.params().isEmpty()) return Relation.Equal;
        var subCompare = con.conArgs()
          .zipView(ctor.params())
          .map(sub -> compare(sub._1.term(), sub._2));
        // compare one level deeper for sub-ctor-patterns like `cons (suc x) xs`, see FoetusLimitation.aya
        // return subCompare.anyMatch(r -> r != Relation.Unknown) ? Relation.Equal : Relation.Unknown;
        return subCompare.max();
      }
      var subCompare = ctor.params().view().map(sub -> compare(lhs, sub));
      return subCompare.anyMatch(r -> r != Relation.Unknown)
        ? Relation.LessThan : Relation.Unknown;
    } else if (rhs instanceof Pat.Bind bind) {
      if (lhs instanceof RefTerm ref)
        return ref.var() == bind.bind() ? Relation.Equal : Relation.Unknown;
      if (headOf(lhs) instanceof RefTerm ref)
        return ref.var() == bind.bind() ? Relation.LessThan : Relation.Unknown;
    }
    return Relation.Unknown;
  }

  /** @return the head of application or projection */
  private @NotNull Term headOf(@NotNull Term term) {
    return switch (term) {
      case ElimTerm.App app -> headOf(app.of());
      case ElimTerm.Proj proj -> headOf(proj.of());
      case CallTerm.Access access -> headOf(access.of());
      default -> term;
    };
  }

  @Override public void visitMatching(@NotNull Matching matching, CallGraph<Def, Term.Param> graph) {
    this.currentMatching.value = matching;
    DefConsumer.super.visitMatching(matching, graph);
    this.currentMatching.value = null;
  }

  @Override public Unit visitFnCall(CallTerm.@NotNull Fn fnCall, CallGraph<Def, Term.Param> graph) {
    resolveCall(fnCall, graph);
    return DefConsumer.super.visitFnCall(fnCall, graph);
  }

  @Override public Unit visitConCall(CallTerm.@NotNull Con conCall, CallGraph<Def, Term.Param> graph) {
    resolveCall(conCall, graph);
    return DefConsumer.super.visitConCall(conCall, graph);
  }

  @Override public Unit visitDataCall(CallTerm.@NotNull Data dataCall, CallGraph<Def, Term.Param> graph) {
    resolveCall(dataCall, graph);
    return DefConsumer.super.visitDataCall(dataCall, graph);
  }

  @Override public Unit visitStructCall(CallTerm.@NotNull Struct structCall, CallGraph<Def, Term.Param> graph) {
    resolveCall(structCall, graph);
    return DefConsumer.super.visitStructCall(structCall, graph);
  }

  @Override public Unit visitAccess(CallTerm.@NotNull Access term, CallGraph<Def, Term.Param> defCallGraph) {
    resolveCall(term, defCallGraph);
    return DefConsumer.super.visitAccess(term, defCallGraph);
  }

  @Override public Unit visitPrimCall(@NotNull CallTerm.Prim prim, CallGraph<Def, Term.Param> graph) {
    resolveCall(prim, graph);
    return DefConsumer.super.visitPrimCall(prim, graph);
  }
}
