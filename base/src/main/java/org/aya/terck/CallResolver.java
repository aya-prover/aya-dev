// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import kala.collection.mutable.MutableSet;
import kala.value.MutableValue;
import org.aya.core.Matching;
import org.aya.core.def.Def;
import org.aya.core.def.FnDef;
import org.aya.core.pat.Pat;
import org.aya.core.term.*;
import org.aya.core.visitor.DefConsumer;
import org.aya.ref.DefVar;
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
  @NotNull MutableValue<Matching> currentMatching,
  @NotNull CallGraph<Def, Term.Param> graph
) implements DefConsumer {
  public CallResolver(@NotNull FnDef fn, @NotNull MutableSet<Def> targets, @NotNull CallGraph<Def, Term.Param> graph) {
    this(fn, targets, MutableValue.create(), graph);
  }

  private void resolveCall(@NotNull CallTerm callTerm) {
    if (!(callTerm.ref() instanceof DefVar<?, ?> defVar)) return;
    var callee = ((Def) defVar.core);
    if (!targets.contains(callee)) return;
    // TODO: reduce arguments? I guess no. see https://github.com/agda/agda/issues/2403
    var matrix = new CallMatrix<>(callTerm, caller, callee, caller.telescope, callee.telescope());
    fillMatrix(callTerm, callee, matrix);
    graph.put(matrix);
  }

  private void fillMatrix(@NotNull CallTerm callTerm, @NotNull Def callee, CallMatrix<Def, Term.Param> matrix) {
    var matching = currentMatching.get();
    if (matching == null) return;
    for (var domThing : matching.patterns().zipView(caller.telescope)) {
      for (var codomThing : callTerm.args().zipView(callee.telescope())) {
        var relation = compare(codomThing._1.term(), domThing._1);
        matrix.set(domThing._2, codomThing._2, relation);
      }
    }
  }

  /** foetus dependencies */
  private @NotNull Relation compare(@NotNull Term term, @NotNull Pat pat) {
    return switch (pat) {
      case Pat.Ctor ctor -> switch (term) {
        case CallTerm.Con con -> {
          if (con.ref() != ctor.ref()) yield Relation.Unknown;
          if (ctor.params().isEmpty()) yield Relation.Equal;
          var subCompare = con.conArgs()
            .zipView(ctor.params())
            .map(sub -> compare(sub._1.term(), sub._2));
          // compare one level deeper for sub-ctor-patterns like `cons (suc x) xs`, see FoetusLimitation.aya
          // return subCompare.anyMatch(r -> r != Relation.Unknown) ? Relation.Equal : Relation.Unknown;
          yield subCompare.max();
        }
        // TODO[literal]: We may convert constructor call to literals to avoid possible stack overflow?
        case LitTerm.ShapedInt lit -> compare(lit.constructorForm(), ctor);
        default -> {
          var subCompare = ctor.params().view().map(sub -> compare(term, sub));
          yield subCompare.anyMatch(r -> r != Relation.Unknown) ? Relation.LessThan : Relation.Unknown;
        }
      };
      case Pat.Bind bind -> {
        if (term instanceof RefTerm ref)
          yield ref.var() == bind.bind() ? Relation.Equal : Relation.Unknown;
        if (headOf(term) instanceof RefTerm ref)
          yield ref.var() == bind.bind() ? Relation.LessThan : Relation.Unknown;
        yield Relation.Unknown;
      }
      case Pat.ShapedInt intPat -> switch (term) {
        case LitTerm.ShapedInt intTerm -> {
          if (intTerm.shape() != intPat.shape()) yield Relation.Unknown;
          yield Relation.fromCompare(Integer.compare(intTerm.repr(), intPat.repr()));
        }
        // TODO[literal]: We may convert constructor call to literals to avoid possible stack overflow?
        case CallTerm.Con con -> compare(con, intPat.constructorForm());
        default -> compare(term, intPat.constructorForm());
      };
      default -> Relation.Unknown;
    };
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

  @Override public void visitMatching(@NotNull Matching matching) {
    this.currentMatching.set(matching);
    DefConsumer.super.visitMatching(matching);
    this.currentMatching.set(null);
  }

  @Override public void pre(@NotNull Term term) {
    if (term instanceof CallTerm call) {
      resolveCall(call);
    }
    DefConsumer.super.pre(term);
  }
}
