// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library;

import kala.collection.SeqLike;
import org.aya.cli.library.source.LibrarySource;
import org.aya.syntax.concrete.stmt.Command;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.ref.ModulePath;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public record ImportResolver(@NotNull ImportLoader loader, @NotNull LibrarySource librarySource) {
  @FunctionalInterface
  public interface ImportLoader {
    @NotNull LibrarySource load(@NotNull ModulePath path, @NotNull SourcePos sourcePos);
  }

  public void resolveStmt(@NotNull SeqLike<Stmt> stmts) throws IOException {
    stmts.forEachChecked(this::resolveStmt);
  }

  public void resolveStmt(@NotNull Stmt stmt) throws IOException {
    switch (stmt) {
      case Command.Module mod -> resolveStmt(mod.contents());
      case Command.Import cmd -> {
        var ids = cmd.path();
        var success = loader.load(ids, cmd.sourcePos());
        var imports = librarySource.imports();
        if (imports.anyMatch(i -> i.moduleName().equals(success.moduleName()))) return;
        imports.append(success);
      }
      default -> {}
    }
  }
}
