// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.ir.struct;

import org.aya.syntax.concrete.stmt.decl.DataCon;
import org.aya.syntax.core.def.FnDef;
import org.jetbrains.annotations.NotNull;

/// Denotes a value in monadic form, i.e., one of the following:
/// - Var reference (including global refs/any atomic value)
/// - Lambda
public sealed interface IrVal permits IrVal.Var, IrVal.Lambda, IrVal.Constructor, IrVal.Func {
  record Constructor(@NotNull DataCon con) implements IrVal {}
  record Func(@NotNull FnDef fn) implements IrVal {}
  record Var(@NotNull IrVarRef ref) implements IrVal {}
  record Lambda(@NotNull IrVarDecl decl, @NotNull IrComp body) implements IrVal {}
}
