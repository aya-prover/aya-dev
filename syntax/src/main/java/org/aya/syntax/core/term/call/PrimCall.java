// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.call;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.TermVisitor;
import org.aya.syntax.concrete.stmt.decl.PrimDecl;
import org.aya.syntax.core.def.PrimDef;
import org.aya.syntax.core.def.PrimDefLike;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.DefVar;
import org.jetbrains.annotations.NotNull;

public record PrimCall(
  @Override @NotNull PrimDefLike ref,
  @Override int ulift,
  @Override @NotNull ImmutableSeq<@NotNull Term> args
) implements Callable.SharableCall {
  public @NotNull PrimCall update(@NotNull ImmutableSeq<Term> args) {
    return args.sameElements(args(), true) ? this : new PrimCall(ref, ulift, args);
  }
  public PrimCall(@NotNull PrimDefLike prim) { this(prim, 0, ImmutableSeq.empty()); }

  @Override public @NotNull Term descent(@NotNull TermVisitor visitor) {
    return update(Callable.descent(args, visitor));
  }

  public PrimCall(
    @NotNull DefVar<@NotNull PrimDef, PrimDecl> ref,
    int ulift, @NotNull ImmutableSeq<@NotNull Term> args
  ) {
    this(new PrimDef.Delegate(ref), ulift, args);
  }
}
