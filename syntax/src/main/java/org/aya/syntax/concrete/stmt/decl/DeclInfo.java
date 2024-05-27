// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete.stmt.decl;

import org.aya.syntax.concrete.stmt.BindBlock;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record DeclInfo(
  @NotNull Stmt.Accessibility accessibility,
  @NotNull SourcePos sourcePos,
  @NotNull SourcePos entireSourcePos,
  @Nullable OpDecl.OpInfo opInfo,
  @NotNull BindBlock bindBlock
) {
}
