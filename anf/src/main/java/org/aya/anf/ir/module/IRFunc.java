// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.ir.module;

import org.aya.anf.ir.struct.IRStmt;
import org.aya.generic.Modifier;
import org.aya.syntax.core.def.FnDef;
import org.jetbrains.annotations.NotNull;

public record IRFunc(
  @NotNull FuncAttr attr,
  @NotNull IRStmt body
) {

  public record FuncAttr(boolean tailRec) {
    public static final FuncAttr DEFAULT = new FuncAttr(false);

    public static FuncAttr of(@NotNull FnDef def) {
      return new FuncAttr(def.modifiers().contains(Modifier.Tailrec));
    }
  }

  public @NotNull String debugRender() {
    return "";
  }
}
