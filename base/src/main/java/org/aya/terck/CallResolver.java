// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableSet;
import kala.tuple.Tuple;
import kala.value.MutableValue;
import org.aya.core.def.Def;
import org.aya.core.def.FnDef;
import org.aya.core.def.PrimDef;
import org.aya.core.pat.Pat;
import org.aya.core.term.*;
import org.aya.core.visitor.DefConsumer;
import org.aya.generic.util.NormalizeMode;
import org.aya.ref.DefVar;
import org.aya.tyck.tycker.TyckState;
import org.aya.util.Arg;
import org.aya.util.terck.CallGraph;
import org.aya.util.terck.CallMatrix;
import org.aya.util.terck.Relation;
import org.jetbrains.annotations.NotNull;

/**
 * Resolve calls and build call graph of recursive functions,
 * after {@link org.aya.tyck.StmtTycker}.
 *
 * @param targets only search calls to those definitions
 * @author kiva
 */
public record CallResolver(
  @NotNull PrimDef.Factory factory,
  @NotNull FnDef caller,
  @NotNull MutableSet<Def> targets,
  @NotNull MutableValue<Term.Matching> currentMatching,
  @NotNull CallGraph<Callable, Def, Term.Param> graph
) implements DefConsumer {
  public CallResolver(
    @NotNull PrimDef.Factory factory, @NotNull FnDef fn,
    @NotNull MutableSet<Def> targets,
    @NotNull CallGraph<Callable, Def, Term.Param> graph
  ) {
    this(factory, fn, targets, MutableValue.create(), graph);
  }

  private @NotNull Term whnf(@NotNull Term term) {
    return term.normalize(new TyckState(factory), NormalizeMode.WHNF);
  }

  private void resolveCall(@NotNull Callable callable) {
    if (!(callable.ref() instanceof DefVar<?, ?> defVar)) return;
    var callee = (Def) defVar.core;
    if (!targets.contains(callee)) return;
    var matrix = new CallMatrix<>(callable, caller, callee, caller.telescope, callee.telescope());
    fillMatrix(callable, callee, matrix);
    graph.put(matrix);
  }

  private void fillMatrix(@NotNull Callable callable, @NotNull Def callee, CallMatrix<?, Def, Term.Param> matrix) {
    var matching = currentMatching.get();
    var domThings = matching != null
      // If we are in a matching, the caller is defined by pattern matching.
      // We should compare patterns with callee arguments.
      ? matching.patterns().zipView(caller.telescope)
      // No matching, the caller is a simple function (not defined by pattern matching).
      // We should compare caller telescope with callee arguments.
      : caller.telescope.view().map(p -> Tuple.of(p.toPat(), p));
    var codomThings = callable.args().zipView(callee.telescope());
    for (var domThing : domThings) {
      for (var codomThing : codomThings) {
        var relation = compare(codomThing.component1().term(), domThing.component1().term());
        matrix.set(domThing.component2(), codomThing.component2(), relation);
      }
    }
  }

  /** foetus dependencies */
  private @NotNull Relation compare(@NotNull Term term, @NotNull Pat pat) {
    return switch (pat) {
      case Pat.Ctor ctor -> switch (term) {
        case ConCall con -> {
          if (con.ref() != ctor.ref() || !con.conArgs().sizeEquals(ctor.params())) yield Relation.unk();
          var attempt = compareConArgs(con.conArgs(), ctor);
          // Reduce arguments and compare again. This may cause performance issues (but not observed yet [2022-11-07]),
          // see https://github.com/agda/agda/issues/2403 for more information.
          if (attempt == Relation.unk()) attempt = compareConArgs(con.conArgs().map(a -> a.descent(this::whnf)), ctor);
          yield attempt;
        }
        // TODO[literal]: We may convert constructor call to literals to avoid possible stack overflow?
        case IntegerTerm lit -> compare(lit.constructorForm(), ctor);
        default -> {
          var subCompare = ctor.params().view().map(sub -> compare(term, sub.term()));
          var attempt = subCompare.anyMatch(r -> r != Relation.unk()) ? Relation.lt() : Relation.unk();
          if (attempt == Relation.unk()) {
            yield switch (whnf(term)) {
              case ConCall con -> compare(con, ctor);
              case IntegerTerm lit -> compare(lit, ctor);
              // This is related to the predicativity issue mentioned in #907
              case PAppTerm papp -> {
                var head = papp.of();
                while (head instanceof PAppTerm papp2) head = papp2.of();
                yield compare(head, ctor);
              }
              default -> attempt;
            };
          }
          yield attempt;
        }
      };
      case Pat.Bind bind -> {
        if (term instanceof RefTerm ref)
          yield ref.var() == bind.bind() ? Relation.eq() : Relation.unk();
        if (headOf(term) instanceof RefTerm ref)
          yield ref.var() == bind.bind() ? Relation.lt() : Relation.unk();
        yield Relation.unk();
      }
      case Pat.ShapedInt intPat -> switch (term) {
        case IntegerTerm intTerm -> {
          // ice: by well-typedness, we don't need to compareShape
          if (intTerm.recognition().shape() != intPat.recognition().shape()) yield Relation.unk();
          yield Relation.fromCompare(Integer.compare(intTerm.repr(), intPat.repr()));
        }
        // TODO[literal]: We may convert constructor call to literals to avoid possible stack overflow?
        case ConCall con -> compare(con, intPat.constructorForm());
        default -> compare(term, intPat.constructorForm());
      };
      default -> Relation.unk();
    };
  }

  private Relation compareConArgs(@NotNull ImmutableSeq<Arg<Term>> conArgs, @NotNull Pat.Ctor ctor) {
    var subCompare = conArgs.zipView(ctor.params()).map(sub -> compare(sub.component1().term(), sub.component2().term()));
    return subCompare.foldLeft(Relation.eq(), Relation::mul);
  }

  /** @return the head of application or projection */
  private @NotNull Term headOf(@NotNull Term term) {
    return switch (term) {
      case AppTerm app -> headOf(app.of());
      case ProjTerm proj -> headOf(proj.of());
      case FieldTerm access -> headOf(access.of());
      default -> term;
    };
  }

  @Override public void accept(@NotNull Term.Matching matching) {
    this.currentMatching.set(matching);
    DefConsumer.super.accept(matching);
    this.currentMatching.set(null);
  }

  @Override public @NotNull void pre(@NotNull Term term) {
    // TODO: Rework error reporting to include the original call
    // term = whnf(term);
    if (term instanceof Callable call) resolveCall(call);
    DefConsumer.super.pre(term);
  }
}
