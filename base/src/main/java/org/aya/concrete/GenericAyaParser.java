// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourceFileLocator;
import org.aya.concrete.stmt.Stmt;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public interface GenericAyaParser {
  @NotNull Expr expr(@NotNull String code, @NotNull SourcePos overridingSourcePos);
  @NotNull ImmutableSeq<Stmt> program(@NotNull SourceFile sourceFile);
  default @NotNull ImmutableSeq<Stmt> program(
    @NotNull SourceFileLocator locator,
    @NotNull Path path
  ) throws IOException {
    var sourceFile = new SourceFile(locator.displayName(path).toString(), path, Files.readString(path));
    return program(sourceFile);
  }
  @NotNull Reporter reporter();
}
