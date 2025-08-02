// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.producer;

import kala.collection.immutable.ImmutableSeq;
import org.aya.intellij.GenericNode;
import org.aya.syntax.GenericAyaProgram;
import org.aya.syntax.concrete.stmt.Stmt;
import org.jetbrains.annotations.NotNull;

public record NodedAyaProgram(@NotNull ImmutableSeq<Stmt> program,
                              @NotNull GenericNode<?> root) implements GenericAyaProgram {
}
