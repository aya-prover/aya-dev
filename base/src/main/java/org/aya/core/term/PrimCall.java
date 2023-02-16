// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.def.PrimDef;
import org.aya.core.pat.Pat;
import org.aya.ref.DefVar;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public record PrimCall(
  @Override @NotNull DefVar<PrimDef, TeleDecl.PrimDecl> ref,
  @NotNull PrimDef.ID id,
  @Override int ulift,
  @Override @NotNull ImmutableSeq<Arg<@NotNull Term>> args
) implements Callable.DefCall {
  public @NotNull PrimCall update(@NotNull ImmutableSeq<Arg<Term>> args) {
    return args.sameElements(args(), true) ? this : new PrimCall(ref, ulift, args);
  }

  @Override public @NotNull PrimCall descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
    return update(args.map(arg -> arg.descent(f)));
  }

  public PrimCall(@NotNull DefVar<@NotNull PrimDef, TeleDecl.PrimDecl> ref,
                  int ulift, @NotNull ImmutableSeq<Arg<@NotNull Term>> args) {
    this(ref, ref.core.id, ulift, args);
  }

}
