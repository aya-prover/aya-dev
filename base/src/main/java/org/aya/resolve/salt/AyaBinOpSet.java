// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.salt;

import kala.collection.immutable.ImmutableSeq;
import org.aya.resolve.error.OperatorError;
import org.aya.syntax.core.def.TyckAnyDef;
import org.aya.tyck.tycker.Problematic;
import org.aya.util.binop.BinOpSet;
import org.aya.util.binop.OpDecl;
import org.aya.util.position.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

public final class AyaBinOpSet extends BinOpSet implements Problematic {
  public final @NotNull Reporter reporter;
  public AyaBinOpSet(@NotNull Reporter reporter) { this.reporter = reporter; }
  @Override public @NotNull Reporter reporter() { return reporter; }
  @Override public boolean equals(@NotNull OpDecl lhs, @NotNull OpDecl rhs) {
    if (lhs instanceof TyckAnyDef<?> wrapper) lhs = wrapper.ref.concrete;
    if (rhs instanceof TyckAnyDef<?> wrapper) rhs = wrapper.ref.concrete;
    return lhs == rhs;
  }
  @Override protected void reportSelfBind(@NotNull SourcePos sourcePos) {
    fail(new OperatorError.SelfBind(sourcePos));
  }

  @Override protected void reportCyclic(ImmutableSeq<ImmutableSeq<BinOP>> cycles) {
    cycles.forEach(c -> fail(new OperatorError.Circular(c)));
  }
}
