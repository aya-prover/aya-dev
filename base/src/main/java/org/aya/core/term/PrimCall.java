// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.def.PrimDef;
import org.aya.ref.DefVar;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

public record PrimCall(
  @Override @NotNull DefVar<PrimDef, TeleDecl.PrimDecl> ref,
  @NotNull PrimDef.ID id,
  @Override int ulift,
  @Override @NotNull ImmutableSeq<Arg<@NotNull Term>> args
) implements Callable.DefCall {
  public PrimCall(@NotNull DefVar<@NotNull PrimDef, TeleDecl.PrimDecl> ref,
                  int ulift, @NotNull ImmutableSeq<Arg<@NotNull Term>> args) {
    this(ref, ref.core.id, ulift, args);
  }

}
