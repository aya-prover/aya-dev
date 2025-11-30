// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.call;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.TermVisitor;
import org.aya.syntax.core.def.FnDefLike;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

public record FnCall(
  @Override @NotNull FnDefLike ref,
  @Override int ulift,
  @Override @NotNull ImmutableSeq<@NotNull Term> args,
  boolean tailCall
) implements Callable.SharableCall {
  public FnCall(@NotNull FnDefLike ref) { this(ref, 0, ImmutableSeq.empty(), false); }
  public FnCall(@NotNull FnDefLike ref, int ulift, @NotNull ImmutableSeq<@NotNull Term> args) {
    this(ref, ulift, args, false);
  }

  public @NotNull FnCall update(@NotNull ImmutableSeq<Term> args) {
    return args.sameElements(args(), true) ? this : new FnCall(ref, ulift, args, tailCall);
  }

  @Override public @NotNull Term descent(@NotNull TermVisitor visitor) {
    return update(Callable.descent(args, visitor));
  }

  @Override public @NotNull Tele doElevate(int level) {
    return new FnCall(ref, ulift + level, args, tailCall);
  }
}
