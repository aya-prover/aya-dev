// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.def.FnDef;
import org.aya.util.Arg;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

public record FnCall(
  @Override @NotNull DefVar<FnDef, TeleDecl.FnDecl> ref,
  @Override int ulift,
  @Override @NotNull ImmutableSeq<Arg<@NotNull Term>> args
) implements Callable.DefCall {
}
