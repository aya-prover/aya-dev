// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.ref;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.stmt.Generalize;
import org.aya.util.error.SourceNode;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public final class GeneralizedVar implements AnyVar, SourceNode {
  public final @NotNull String name;
  public final @NotNull SourcePos sourcePos;
  public Generalize owner;

  public GeneralizedVar(@NotNull String name, @NotNull SourcePos sourcePos) {
    this.name = name;
    this.sourcePos = sourcePos;
  }

  public @NotNull LocalVar toLocal() {
    return new LocalVar(name, sourcePos, new GenerateKind.Generalized(this));
  }

  public @NotNull Expr.Param toParam(boolean explicit) {
    assert owner != null : "Sanity check";
    return owner.toExpr(explicit, toLocal());
  }

  public @NotNull String name() { return name; }
  @Override public @NotNull SourcePos sourcePos() { return sourcePos; }
}
