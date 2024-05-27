// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.repr;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.generic.stmt.Shaped;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.ConDefLike;
import org.aya.syntax.core.def.FnDefLike;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * IntegerOps acts like a DefVar with special reduce rule. So it is not a {@link Term}.
 *
 * @see org.aya.syntax.core.term.call.RuleReducer
 */
public sealed interface IntegerOps<Def extends AnyDef> extends Shaped.Applicable<Def> {
  record ConRule(
    @Override @NotNull ConDefLike ref,
    @NotNull IntegerTerm zero
  ) implements IntegerOps<ConDefLike> {
    @Override public @Nullable Term apply(@NotNull ImmutableSeq<Term> args) {
      // zero
      if (args.isEmpty()) return zero;

      // suc
      assert args.sizeEquals(1);
      var arg = args.get(0);
      if (arg instanceof IntegerTerm intTerm) return intTerm.map(x -> x + 1);
      return null;
    }
    @Override public @NotNull ConRule descent(@NotNull IndexedFunction<Term, Term> f) {
      return this;
    }
  }

  record FnRule(
    @Override @NotNull FnDefLike ref,
    @NotNull Kind kind
  ) implements IntegerOps<FnDefLike> {
    public enum Kind {
      Add, SubTrunc
    }

    @Override
    public @Nullable Term apply(@NotNull ImmutableSeq<Term> args) {
      return switch (kind) {
        case Add -> {
          assert args.sizeEquals(2);
          if (args.get(0) instanceof IntegerTerm ita && args.get(1) instanceof IntegerTerm itb) {
            yield ita.map(x -> x + itb.repr());
          }

          yield null;
        }
        case SubTrunc -> {
          assert args.sizeEquals(2);
          if (args.get(0) instanceof IntegerTerm ita && args.get(1) instanceof IntegerTerm itb) {
            yield ita.map(x -> Math.max(x - itb.repr(), 0));
          }

          yield null;
        }
      };
    }
    @Override public @NotNull FnRule descent(@NotNull IndexedFunction<Term, Term> f) {
      return this;
    }
  }
}
