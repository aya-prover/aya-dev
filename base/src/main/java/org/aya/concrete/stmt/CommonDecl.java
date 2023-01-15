// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Common parts of concrete definitions.
 * In particular, it does not assume the definition to have a telescope.
 *
 * @author ice1000
 * @apiNote This class should only be used in extends and permits clause. Use {@link Decl} elsewhere instead.
 * @see Decl
 */
public sealed abstract class CommonDecl implements Decl permits ClassDecl, TeleDecl, TeleDecl.DataCtor, TeleDecl.StructField {
  public final @NotNull Accessibility accessibility;
  public final @NotNull SourcePos sourcePos;
  public final @NotNull SourcePos entireSourcePos;
  public final @Nullable OpInfo opInfo;
  public final @NotNull BindBlock bindBlock;

  protected CommonDecl(
    @NotNull SourcePos sourcePos,
    @NotNull SourcePos entireSourcePos,
    @NotNull Accessibility accessibility,
    @Nullable OpDecl.OpInfo opInfo,
    @NotNull BindBlock bindBlock
  ) {
    this.sourcePos = sourcePos;
    this.entireSourcePos = entireSourcePos;
    this.accessibility = accessibility;
    this.opInfo = opInfo;
    this.bindBlock = bindBlock;
  }

  @Override public @NotNull BindBlock bindBlock() {
    return bindBlock;
  }

  @Override public @NotNull SourcePos sourcePos() {
    return sourcePos;
  }

  @Override public @NotNull SourcePos entireSourcePos() {
    return entireSourcePos;
  }

  @Override public @NotNull Accessibility accessibility() {
    return accessibility;
  }

  @Override public @Nullable OpInfo opInfo() {
    return opInfo;
  }

  @Override public String toString() {
    return getClass().getSimpleName() + "[" + ref().name() + "]";
  }
}
