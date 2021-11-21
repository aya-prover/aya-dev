// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.stmt.Command;
import org.aya.concrete.stmt.Stmt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

public record ImportResolver(
  @NotNull ImportResolver.ImportFileLoader loader,
  @NotNull Imports imports
) {
  record Imports(
    @NotNull Path self,
    @NotNull DynamicSeq<Imports> imports
  ) {
    public @NotNull Path canonicalPath() {
      return ResolveInfo.canonicalize(self);
    }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Imports imports = (Imports) o;
      return canonicalPath().equals(imports.canonicalPath());
    }

    @Override public int hashCode() {
      return Objects.hash(self);
    }
  }

  interface ImportFileLoader {
    @Nullable Imports loadFile(@NotNull ImmutableSeq<String> mod);
  }

  public void resolveStmt(@NotNull SeqLike<Stmt> stmts) {
    stmts.forEach(this::resolveStmt);
  }

  public void resolveStmt(@NotNull Stmt stmt) {
    switch (stmt) {
      case Command.Module mod -> resolveStmt(mod.contents());
      case Command.Import cmd -> {
        var ids = cmd.path().ids();
        var success = loader.loadFile(ids);
        if (success != null) imports.imports.append(success);
      }
      default -> {}
    }
  }
}
