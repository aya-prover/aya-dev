// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.call;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.syntax.concrete.stmt.decl.DataCon;
import org.aya.syntax.core.def.ConDef;
import org.aya.syntax.core.def.ConDefLike;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.DefVar;
import org.jetbrains.annotations.NotNull;

/**
 * @param conArgs the arguments to the constructor, see {@link ConDef#selfTele}
 */
public record ConCall(
  @Override @NotNull ConCall.Head head,
  @Override @NotNull ImmutableSeq<Term> conArgs
) implements ConCallLike {
  public ConCall(
    @NotNull DefVar<ConDef, ? extends DataCon> ref,
    int ulift,
    @NotNull ImmutableSeq<@NotNull Term> ownerArgs,
    @NotNull ImmutableSeq<@NotNull Term> conArgs
  ) {
    this(new Head(new ConDef.Delegate(ref), ulift, ownerArgs), conArgs);
  }

  public @NotNull ConCall update(@NotNull Head head, @NotNull ImmutableSeq<Term> conArgs) {
    return head == head() && conArgs.sameElements(conArgs(), true) ? this : new ConCall(head, conArgs);
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(head.descent(f), Callable.descent(conArgs, f));
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
