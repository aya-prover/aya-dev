// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.call;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.syntax.core.def.ClassDefLike;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.marker.Formation;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.aya.syntax.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

/**
 * ClassCall is a very special construction in Aya.
 * <ul>
 *   <li>It is like a type when partially instantiated -- the type of "specifications" of the rest of the fields.</li>
 *   <li>It is like a term when fully instantiated, whose type can be anything.</li>
 *   <li>It can be applied like a function, which essentially inserts the nearest missing field.</li>
 * </ul>
 *
 * @author kiva, ice1000
 */
public record ClassCall(
  @NotNull LocalVar self,
  @NotNull ClassDefLike ref,
  @Override int ulift,
  @NotNull ImmutableSeq<Term> args
) implements StableWHNF, Formation {
  public @NotNull ClassCall update(@NotNull ImmutableSeq<Term> args) {
    return this.args.sameElements(args, true)
      ? this : new ClassCall(self, ref, ulift, args);
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(args.map(t -> f.apply(0, t)));
  }

  @Override public @NotNull Term doElevate(int level) {
    return new ClassCall(self, ref, ulift + level, args);
  }
}
