// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete.stmt.decl;

import org.aya.syntax.concrete.Named;
import org.aya.syntax.concrete.stmt.BindBlock;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.util.binop.OpDecl;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record DeclInfo(
  @NotNull Stmt.Accessibility accessibility,
  @Override @NotNull SourcePos nameSourcePos,
  @NotNull SourcePos entireSourcePos,
  @Nullable OpDecl.OpInfo opInfo,
  @NotNull BindBlock bindBlock
) implements Named {
}
