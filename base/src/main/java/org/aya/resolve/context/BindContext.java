// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.mutable.MutableList;
import kala.control.Option;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.position.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Introduces a locally bound variable to the context.
 *
 * @author re-xyr
 */
public record BindContext(
  @NotNull Context parent,
  @NotNull String name,
  @NotNull LocalVar ref
) implements Context {
  @Override public @NotNull Context parent() { return parent; }

  @Override public @NotNull Path underlyingFile() {
    return parent.underlyingFile();
  }

  @Override public MutableList<LocalVar> collect(@NotNull MutableList<LocalVar> container) {
    if (container.noneMatch(v -> Objects.equals(v.name(), ref.name()))) container.append(ref);
    return parent.collect(container);
  }

  @Override
  public @Nullable Candidate<AnyVar> getCandidateLocalMaybe(@NotNull String name, @NotNull SourcePos sourcePos) {
    if (name.equals(this.name)) return new Candidate.Defined<>(ref);
    return null;
  }

  @Override
  public @Nullable Option<AnyVar> getQualifiedLocalMaybe(
    @NotNull ModuleName.Qualified modName,
    @NotNull String name,
    @NotNull SourcePos sourcePos,
    @NotNull Reporter reporter
  ) {
    return parent.getQualifiedLocalMaybe(modName, name, sourcePos, reporter);
  }

  @Override
  public @Nullable ModuleExport getModuleLocalMaybe(@NotNull ModuleName.Qualified modName) {
    return null;
  }
}
