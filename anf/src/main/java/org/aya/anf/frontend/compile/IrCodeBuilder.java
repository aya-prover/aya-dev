// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.frontend.compile;

import kala.collection.immutable.ImmutableSeq;
import org.aya.anf.ir.struct.IrComp;
import org.aya.anf.ir.struct.LetClause;
import org.jetbrains.annotations.NotNull;

// XXX: currently `binds` are singleton sequences, change this after parallel moves
// are a thing.
public record IrCodeBuilder(
  @NotNull LoweringContext ctx,
  @NotNull ImmutableSeq<LetClause> binds
) {

  public @NotNull IrComp finish(@NotNull IrComp expr) {
    return binds.foldRight(expr, IrComp.Let::new);
  }
}
