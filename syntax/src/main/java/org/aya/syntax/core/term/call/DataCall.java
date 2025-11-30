// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.call;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.TermVisitor;
import org.aya.syntax.core.def.DataDefLike;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.marker.Formation;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.jetbrains.annotations.NotNull;

public record DataCall(
  @Override @NotNull DataDefLike ref,
  @Override int ulift,
  @Override @NotNull ImmutableSeq<@NotNull Term> args
) implements Callable.SharableCall, StableWHNF, Formation {
  public DataCall(@NotNull DataDefLike ref) { this(ref, 0, ImmutableSeq.empty()); }
  public @NotNull DataCall update(@NotNull ImmutableSeq<Term> args) {
    return args.sameElements(args(), true) ? this : new DataCall(ref, ulift, args);
  }

  @Override public @NotNull Term descent(@NotNull TermVisitor visitor) {
    return update(Callable.descent(args, visitor));
  }

  @Override public @NotNull Tele doElevate(int level) {
    return new DataCall(ref, ulift + level, args);
  }
}
