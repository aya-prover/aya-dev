// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt.decl;

import org.aya.concrete.stmt.BindBlock;
import org.aya.concrete.stmt.Stmt;
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
  /** @see org.aya.generic.Modifier */
  public enum Personality {
    /** Denotes that the definition is a normal definition (default behavior) */
    NORMAL,
    /** Denotes that the definition is an example (same as normal, but in separated context) */
    EXAMPLE,
    /** Denotes that the definition is a counterexample (errors expected, in separated context) */
    COUNTEREXAMPLE,
  }
}
