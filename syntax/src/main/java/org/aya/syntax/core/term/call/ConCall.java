// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.call;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.generic.TermVisitor;
import org.aya.syntax.core.def.ConDef;
import org.aya.syntax.core.def.ConDefLike;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

/**
 * @param conArgs the arguments to the constructor, see {@link ConDef#selfTele}
 */
public record ConCall(
  @Override @NotNull ConCall.Head head,
  @Override @NotNull ImmutableSeq<Term> conArgs
) implements ConCallLike, Callable.SharableCall {
  public ConCall(@NotNull ConDefLike con) {
    this(new Head(con, 0, ImmutableSeq.empty()), ImmutableSeq.empty());
  }
  public @NotNull ConCall update(@NotNull Head head, @NotNull ImmutableSeq<Term> conArgs) {
    return head == head() && conArgs.sameElements(conArgs(), true) ? this : new ConCall(head, conArgs);
  }

  @Override public @NotNull Term descent(@NotNull TermVisitor visitor) {
    return update(head.descent(visitor), Callable.descent(conArgs, visitor));
  }

  public ConCall(
    @NotNull ConDefLike ref,
    @NotNull ImmutableSeq<@NotNull Term> ownerArgs,
    int ulift,
    @NotNull ImmutableSeq<@NotNull Term> conArgs
  ) {
    this(new Head(ref, ulift, ownerArgs), conArgs);
  }

  @Override public @NotNull Tele doElevate(int level) {
    return new ConCall(new Head(head.ref(), head.ulift() + level, head.ownerArgs()), conArgs);
  }
}
