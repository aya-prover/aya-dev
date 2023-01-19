// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import org.aya.concrete.stmt.Stmt;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * @author re-xyr
 * @apiNote in each file's dependency tree there should be one and only one EmptyContext which is also the tree root.
 * @implNote EmptyContext is the context storing the underlying file, and its Reporter in the resolving stage.
 */
public record EmptyContext(
  @NotNull Reporter reporter,
  @NotNull Path underlyingFile
) implements Context {
  @Override public @Nullable Context parent() {
    return null;
  }

  @Override public @Nullable ContextUnit getUnqualifiedLocalMaybe(
    @NotNull String name,
    @Nullable Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos
  ) {
    return null;
  }

  @Override
  public @Nullable ContextUnit getQualifiedLocalMaybe(
    @NotNull ModulePath.Qualified modName,
    @NotNull String name,
    @Nullable Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos
  ) {
    return null;
  }

  @Override
  public @Nullable ModuleExport getModuleLocalMaybe(@NotNull ModulePath modName) {
    return null;
  }
}
