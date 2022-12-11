// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import org.aya.concrete.Expr;
import org.aya.core.def.ClassDef;
import org.aya.core.term.Term;
import org.aya.resolve.context.Context;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

/**
 * Concrete classable definitions, corresponding to {@link ClassDef}.
 *
 * @author zaoqi
 * @see Decl
 */
public non-sealed/*sealed*/ abstract class ClassDecl extends CommonDecl implements Decl.Resulted<Term>, Decl.TopLevel {
  private final @NotNull Decl.Personality personality;
  public @Nullable Context ctx = null;
  public @NotNull Expr result;

  @Override public @NotNull Decl.Personality personality() {
    return personality;
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

  @Override public void modifyResult(@NotNull UnaryOperator<Expr> f) {
    result = f.apply(result);
  }

  protected ClassDecl(
    @NotNull SourcePos sourcePos,
    @NotNull SourcePos entireSourcePos,
    @Nullable OpDecl.OpInfo opInfo,
    @NotNull BindBlock bindBlock,
    @NotNull Expr result,
    @NotNull Decl.Personality personality,
    @NotNull Accessibility accessibility
  ) {
    super(sourcePos, entireSourcePos, accessibility, opInfo, bindBlock);
    this.result = result;
    this.personality = personality;
  }

  @Override public String toString() {
    return getClass().getSimpleName() + "[" + ref().name() + "]";
  }
}
