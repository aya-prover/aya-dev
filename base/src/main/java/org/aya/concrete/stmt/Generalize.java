// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.Expr;
import org.aya.ref.LocalVar;
import org.aya.resolve.context.Context;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Generalize implements Stmt {
  @Override public @NotNull Accessibility accessibility() {
    return Accessibility.Private;
  }

  @Override public boolean needTyck(@NotNull ImmutableSeq<String> currentMod) {
    // commands are desugared in the shallow resolver
    return false;
  }

  public final @NotNull SourcePos sourcePos;
  public final @NotNull ImmutableSeq<GeneralizedVar> variables;
  public @NotNull Expr type;
  public @Nullable Context ctx = null;

  public Generalize(@NotNull SourcePos sourcePos, @NotNull ImmutableSeq<GeneralizedVar> variables, @NotNull Expr type) {
    this.sourcePos = sourcePos;
    this.variables = variables;
    this.type = type;
    variables.forEach(variable -> variable.owner = this);
  }

  public @NotNull Expr.Param toExpr(boolean explicit, @NotNull LocalVar ref) {
    return new Expr.Param(ref.definition(), ref, type, explicit);
  }

  public @NotNull ImmutableSeq<Expr.Param> toExpr() {
    return variables.map(one -> toExpr(true, one.toLocal()));
  }

  public @NotNull SourcePos sourcePos() {
    return sourcePos;
  }
}
