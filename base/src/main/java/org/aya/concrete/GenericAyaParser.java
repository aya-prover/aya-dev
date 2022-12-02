// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.Stmt;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;

public interface GenericAyaParser {
  @NotNull Expr expr(@NotNull String code, @NotNull SourcePos overridingSourcePos);
  @NotNull ImmutableSeq<Stmt> program(@NotNull SourceFile sourceFile, @NotNull SourceFile errorReport);
  @TestOnly default @NotNull ImmutableSeq<Stmt> program(@NotNull SourceFile sourceFile) {
    return program(sourceFile, sourceFile);
  }
  default @NotNull ImmutableSeq<Stmt> program(@NotNull GenericAyaFile ayaFile) throws IOException {
    return program(ayaFile.sourceFile(), ayaFile.errorReportSourceFile());
  }
  @NotNull Reporter reporter();
}
