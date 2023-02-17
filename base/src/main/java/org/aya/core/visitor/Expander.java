// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.Set;
import kala.collection.immutable.ImmutableSet;
import kala.collection.mutable.MutableSet;
import org.aya.core.def.PrimDef;
import org.aya.core.term.*;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.tyck.tycker.TyckState;
import org.jetbrains.annotations.NotNull;

public interface Expander extends DeltaExpander, BetaExpander {
  @Override @NotNull default Term post(@NotNull Term term) {
    return BetaExpander.super.post(DeltaExpander.super.post(term));
  }

  record Normalizer(@Override @NotNull TyckState state) implements Expander {}

  record WHNFer(@Override @NotNull TyckState state) implements Expander {
    @Override public @NotNull Term apply(@NotNull Term term) {
      return switch (term) {
        case StableWHNF whnf -> whnf;
        case ConCall con when (con.ref().core == null || con.ref().core.clauses.clauses().isEmpty()) -> con;
        default -> Expander.super.apply(term);
      };
    }
  }

  record ConservativeWHNFer(@Override @NotNull TyckState state, ImmutableSet<DefVar<?, ?>> opaqueVars) implements Expander {
    @Override public @NotNull Term apply(@NotNull Term term) {
      return switch (term) {
        case StableWHNF whnf -> whnf;
        case ConCall con when (con.ref().core == null || con.ref().core.clauses.clauses().isEmpty()) -> con;
        case FnCall fn when opaqueVars.contains(fn.ref()) -> fn;
        default -> Expander.super.apply(term);
      };
    }
  }
  record Tracked(
    @NotNull Set<@NotNull AnyVar> unfolding,
    @NotNull MutableSet<@NotNull AnyVar> unfolded,
    @Override @NotNull TyckState state,
    @NotNull PrimDef.Factory factory
  ) implements Expander {
    @Override public @NotNull Term apply(@NotNull Term term) {
      return switch (term) {
        case FnCall fn -> {
          if (!unfolding.contains(fn.ref())) yield fn;
          unfolded.add(fn.ref());
          yield Expander.super.apply(fn);
        }
        case ConCall con -> {
          if (!unfolding.contains(con.ref())) yield con;
          unfolded.add(con.ref());
          yield Expander.super.apply(con);
        }
        case PrimCall prim -> factory.unfold(prim.id(), prim, state);
        default -> Expander.super.apply(term);
      };
    }
  }
}
