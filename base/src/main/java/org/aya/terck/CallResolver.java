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
    // TODO: source pos of the CallTerm?
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
        var pat = domThing._1;
        var arg = codomThing._1;
        var domain = domThing._2;
        var codomain = codomThing._2;
        var relation = compare(arg.term(), pat);
        matrix.set(domain, codomain, relation);
      }
    }
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
      if (lhs instanceof CallTerm.Con con) {
        if (con.ref() != ctor.ref()) return Relation.Unknown;
        var subCompare = ctor.params()
          .zipView(con.conArgs())
          .map(sub -> compare(sub._2.term(), sub._1));
        if (subCompare.anyMatch(r -> r != Relation.Unknown)) return Relation.Equal;
      }
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
