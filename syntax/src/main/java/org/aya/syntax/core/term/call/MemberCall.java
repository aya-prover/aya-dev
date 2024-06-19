// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.call;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.syntax.core.def.MemberDefLike;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

public record MemberCall(
  @NotNull Term of,
  @Override @NotNull MemberDefLike ref,
  @Override int ulift,
  @Override @NotNull ImmutableSeq<@NotNull Term> args
) implements Callable.Tele {
  private MemberCall update(Term clazz, ImmutableSeq<Term> newArgs) {
    return clazz == of && newArgs.sameElements(args, true) ? this
      : new MemberCall(clazz, ref, ulift, newArgs);
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(f.apply(0, of), Callable.descent(args, f));
  }
}
