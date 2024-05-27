// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.call;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.syntax.concrete.stmt.decl.PrimDecl;
import org.aya.syntax.core.def.PrimDef;
import org.aya.syntax.core.def.TyckAnyDef;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.DefVar;
import org.jetbrains.annotations.NotNull;

public record PrimCall(
  @Override @NotNull TyckAnyDef<PrimDef> ref,
  @Override int ulift,
  @Override @NotNull ImmutableSeq<@NotNull Term> args
) implements Callable.Tele {
  public @NotNull PrimCall update(@NotNull ImmutableSeq<Term> args) {
    return args.sameElements(args(), true) ? this : new PrimCall(ref, ulift, args);
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(Callable.descent(args, f));
  }

  public PrimCall(
    @NotNull DefVar<@NotNull PrimDef, PrimDecl> ref,
    int ulift, @NotNull ImmutableSeq<@NotNull Term> args
  ) {
    this(new PrimDef.Delegate(ref), ulift, args);
  }
}
