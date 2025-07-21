// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.ir.struct;

import org.jetbrains.annotations.NotNull;

public interface IrExpr {
  record Var(@NotNull IrVarRef ref) implements IrExpr { }
  record App(@NotNull IrExprHead head, @NotNull ImmutableSeq<IrArg> args) implements IrExpr { }
  record Lambda(@NotNull IrVarDecl decl, @NotNull IrExpr body) implements IrExpr { }
  record Let(@NotNull LetClause let, @NotNull IrExpr body) implements IrExpr { }
}
