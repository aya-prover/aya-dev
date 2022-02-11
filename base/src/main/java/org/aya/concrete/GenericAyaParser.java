// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.Stmt;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourceFileLocator;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public interface GenericAyaParser {
  @NotNull Expr expr(@NotNull String code, @NotNull SourcePos overridingSourcePos);
  @NotNull ImmutableSeq<Stmt> program(@NotNull SourceFile sourceFile);
  default @NotNull ImmutableSeq<Stmt> program(@NotNull SourceFileLocator l, @NotNull Path p) throws IOException {
    return program(new SourceFile(l.displayName(p).toString(), p, Files.readString(p)));
  }
  @NotNull Reporter reporter();
}
