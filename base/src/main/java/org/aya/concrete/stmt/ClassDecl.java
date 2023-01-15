// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import org.aya.core.def.ClassDef;
import org.aya.resolve.context.Context;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Concrete classable definitions, corresponding to {@link ClassDef}.
 *
 * @author zaoqi
 * @see Decl
 */
public non-sealed/*sealed*/ abstract class ClassDecl extends CommonDecl implements Decl.TopLevel {
  private final @NotNull DeclInfo.Personality personality;
  public @Nullable Context ctx = null;

  @Override public @NotNull DeclInfo.Personality personality() {
    return personality;
  }

  @Override public @Nullable Context getCtx() {
    return ctx;
  }

  @Override public void setCtx(@NotNull Context ctx) {
    this.ctx = ctx;
  }

  protected ClassDecl(@NotNull DeclInfo info, @NotNull DeclInfo.Personality personality) {
    super(info);
    this.personality = personality;
  }
}
