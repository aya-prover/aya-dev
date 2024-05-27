// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * @see GenericAyaFile#parseMe(GenericAyaParser)
 */
public interface GenericAyaParser {
  @NotNull WithPos<Expr> expr(@NotNull String code, @NotNull SourcePos overridingSourcePos);
  @NotNull ImmutableSeq<Stmt> program(@NotNull SourceFile sourceFile, @NotNull SourceFile errorReport);
  @TestOnly @VisibleForTesting default @NotNull ImmutableSeq<Stmt> program(@NotNull SourceFile sourceFile) {
    return program(sourceFile, sourceFile);
  }
  @NotNull Reporter reporter();
}
