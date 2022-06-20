// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import org.aya.concrete.Expr;
import org.aya.core.def.ClassDef;
import org.aya.resolve.context.Context;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An item in the signature, with fields and result type.
 * Concrete definition, corresponding to {@link ClassDef}.
 *
 * @author zaoqi
 */
public non-sealed/*sealed*/ abstract class ClassDecl extends BaseDecl implements TopLevelDecl {
  public @NotNull Expr result;
  public final @NotNull TopLevelDecl.Personality personality;

  public @Nullable Context ctx = null;
  public final @NotNull Accessibility accessibility;

  @Override public @NotNull TopLevelDecl.Personality personality() {
    return personality;
  }

  @Override public @NotNull Accessibility accessibility() {
    return accessibility;
  }

  @Override public @Nullable Context getCtx() {
    return ctx;
  }

  @Override public void setCtx(@NotNull Context ctx) {
    this.ctx = ctx;
  }

  @Override public @NotNull Expr result() {
    return result;
  }

  @Override public void setResult(@NotNull Expr result) {
    this.result = result;
  }

  protected ClassDecl(
    @NotNull SourcePos sourcePos,
    @NotNull SourcePos entireSourcePos,
    @Nullable OpDecl.OpInfo opInfo,
    @NotNull BindBlock bindBlock,
    @NotNull Expr result,
    @NotNull TopTeleDecl.Personality personality,
    @NotNull Accessibility accessibility
  ) {
    super(sourcePos, entireSourcePos, opInfo, bindBlock);
    this.result = result;
    this.personality = personality;
    this.accessibility = accessibility;
  }

  @Override public String toString() {
    return getClass().getSimpleName() + "[" + ref().name() + "]";
  }
}
