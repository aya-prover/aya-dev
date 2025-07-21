// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.frontend.compile;

import kala.collection.immutable.ImmutableSeq;
import org.aya.anf.ir.struct.IrExpr;
import org.aya.anf.ir.struct.IrVarRef;
import org.aya.anf.ir.struct.LetClause;
import org.jetbrains.annotations.NotNull;

public record IrCodeBuilder(
  @NotNull LoweringContext ctx,
  @NotNull ImmutableSeq<LetClause> binds
) {

  public @NotNull IrVarRef lookup(@NotNull String id) {

  }

  public @NotNull IrExpr finish(@NotNull IrExpr expr) {
    return binds.foldRight(expr, IrExpr.Let::new);
  }
}
