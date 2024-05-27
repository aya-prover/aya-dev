// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.repr;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.generic.stmt.Shaped;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.ConDefLike;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ListOps<Def extends AnyDef> extends Shaped.Applicable<Def> {
  record ConRule(
    @Override @NotNull ConDefLike ref,
    @NotNull ListTerm empty
  ) implements ListOps<ConDefLike> {
    @Override public @Nullable Term apply(@NotNull ImmutableSeq<Term> args) {
      // empty
      if (args.sizeEquals(1)) return empty;

      // cons : {A : Type} (x : A) (xs : List A) -> List A
      assert args.sizeEquals(3);
      var x = args.get(1);
      var xs = args.get(2);
      if (xs instanceof ListTerm list)
        return list.map(ys -> ys.prepended(x));
      return null;
    }
    public ConRule update(@NotNull ListTerm empty) {
      return empty == this.empty ? this : new ConRule(ref, empty);
    }
    @Override public @NotNull ConRule descent(@NotNull IndexedFunction<Term, Term> f) {
      return update((ListTerm) f.apply(0, empty));
    }
  }
}
