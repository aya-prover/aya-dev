// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.frontend.compile;

import kala.collection.mutable.MutableList;
import org.aya.anf.ir.struct.IrVarDecl;
import org.aya.anf.misc.NestedEnvLookup;
import org.jetbrains.annotations.NotNull;

public record LoweringContext(
  @NotNull MutableList<IrVarDecl.Generated> unnamedVars,
  @NotNull NestedEnvLookup<IrVarDecl> env

  ) {
  public static LoweringContext create() {
    return new LoweringContext(MutableList.create(), new NestedEnvLookup<>());
  }

  public void bindScope(@NotNull IrVarDecl decl) {
    env.add(decl.identifier(), decl);
  }

  public void exitScope() {
    env.pop();
  }
}
