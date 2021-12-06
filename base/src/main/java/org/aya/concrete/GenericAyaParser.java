// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.Reporter;
import org.aya.concrete.stmt.Stmt;
import org.aya.util.error.SourceFile;
import org.jetbrains.annotations.NotNull;

public interface GenericAyaParser {
  @NotNull Expr expr(@NotNull Reporter reporter, @NotNull String code);
  @NotNull ImmutableSeq<Stmt> program(@NotNull Reporter reporter, @NotNull SourceFile sourceFile);
}
