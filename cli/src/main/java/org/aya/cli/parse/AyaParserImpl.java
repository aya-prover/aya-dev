// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.parse;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.Reporter;
import org.aya.concrete.Expr;
import org.aya.concrete.GenericAyaParser;
import org.aya.concrete.stmt.Stmt;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record AyaParserImpl(@NotNull Reporter reporter) implements GenericAyaParser {
  @Override public @NotNull Expr expr(@NotNull String code, @NotNull SourcePos pos) {
    return AyaParsing.expr(reporter, code, pos);
  }

  @Override public @NotNull ImmutableSeq<Stmt> program(@NotNull SourceFile sourceFile) {
    return AyaParsing.program(reporter, sourceFile);
  }
}
