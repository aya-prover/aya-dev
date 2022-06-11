// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import org.aya.concrete.Expr;
import org.aya.core.def.GenericDef;
import org.aya.resolve.context.Context;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Generic concrete definition, corresponding to {@link GenericDef}.
 *
 * @author zaoqi
 */
public sealed interface TopLevelDecl extends Decl, Stmt permits ClassDecl, TelescopicDecl {
  enum Personality {
    NORMAL,
    EXAMPLE,
    COUNTEREXAMPLE,
  }

  @NotNull Personality personality();

  @Nullable Context getCtx();
  void setCtx(@NotNull Context ctx);

  @NotNull Expr result();
  void setResult(@NotNull Expr result);
}
