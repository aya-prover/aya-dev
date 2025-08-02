// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.concrete.stmt.Stmt;
import org.jetbrains.annotations.NotNull;

public interface GenericAyaProgram {
  @NotNull ImmutableSeq<Stmt> program();

  record Default(@NotNull ImmutableSeq<Stmt> program) implements GenericAyaProgram { }
}
