// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.mutable.MutableList;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.position.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Overrides the {@link #reporter} of {@link #parent}.
 *
 * @see org.aya.resolve.error.NameProblem.UnqualifiedNameNotFoundError#didYouMean
 */
public record ReporterContext(@NotNull Context parent, @NotNull Reporter reporter) implements Context {
  @Override public @NotNull Context parent() { return parent; }
  @Override public @NotNull Reporter reporter() { return reporter; }

  @Override public @NotNull Path underlyingFile() {
    return parent.underlyingFile();
  }

  @Override public MutableList<LocalVar> collect(@NotNull MutableList<LocalVar> container) {
    return parent.collect(container);
  }
  @Override
  public @Nullable Candidate<AnyVar> getCandidateLocalMaybe(@NotNull String name, @NotNull SourcePos sourcePos) {
    return null;
  }

  @Override public @Nullable AnyVar getQualifiedLocalMaybe(
    @NotNull ModuleName.Qualified modName,
    @NotNull String name,
    @NotNull SourcePos sourcePos
  ) throws ResolvingInterruptedException {
    return parent.getQualifiedLocalMaybe(modName, name, sourcePos);
  }

  @Override public @Nullable ModuleExport getModuleLocalMaybe(@NotNull ModuleName.Qualified modName) {
    return null;
  }
}
