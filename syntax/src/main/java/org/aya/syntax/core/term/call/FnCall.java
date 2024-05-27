// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.call;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.syntax.concrete.stmt.decl.FnDecl;
import org.aya.syntax.core.def.FnDef;
import org.aya.syntax.core.def.FnDefLike;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.DefVar;
import org.jetbrains.annotations.NotNull;

public record FnCall(
  @Override @NotNull FnDefLike ref,
  @Override int ulift,
  @Override @NotNull ImmutableSeq<@NotNull Term> args
) implements Callable.Tele {
  public FnCall(
    @NotNull DefVar<FnDef, FnDecl> ref,
    int ulift,
    @NotNull ImmutableSeq<@NotNull Term> args
  ) {
    this(new FnDef.Delegate(ref), ulift, args);
  }

  public @NotNull FnCall update(@NotNull ImmutableSeq<Term> args) {
    return args.sameElements(args(), true) ? this : new FnCall(ref, ulift, args);
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(Callable.descent(args, f));
  }

  @Override public @NotNull Tele doElevate(int level) {
    return new FnCall(ref, ulift + level, args);
  }
}
