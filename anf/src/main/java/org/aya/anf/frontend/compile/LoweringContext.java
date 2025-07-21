// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.frontend.compile;

import kala.collection.mutable.MutableList;
import org.aya.anf.ir.struct.IrVarDecl;
import org.aya.anf.ir.struct.LetClause;
import org.aya.anf.misc.NestedEnvLookup;
import org.aya.syntax.core.def.FnDef;
import org.jetbrains.annotations.NotNull;

public record LoweringContext(
  @NotNull MutableList<IrVarDecl.Generated> unnamedVars,
  @NotNull NestedEnvLookup<IrVarDecl> env

  ) {
  public static LoweringContext empty() {
    return new LoweringContext(MutableList.create(), new NestedEnvLookup<>());
  }

  public static LoweringContext fromFuncDef(final @NotNull FnDef fn) {
    var env = new NestedEnvLookup<IrVarDecl>();
    return new LoweringContext(MutableList.create(), env);
  }

  /// Creates a builder at the current scope, without additional bindings.
  public @NotNull IrCompBuilder buildAtCtx() {
    return new IrCompBuilder(this);
  }

  /// Creates a builder inside a `let` expression rooted at the current scope.
  public @NotNull IrCompBuilder buildLet(@NotNull LetClause clause) {
    return new IrCompBuilder(this, clause);
  }

  public void bindScope(@NotNull IrVarDecl decl) {
    env.add(decl.identifier(), decl);
  }

  public void exitScope() {
    env.pop();
  }
}
