// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.immutable.ImmutableSeq;
import org.aya.ref.AnyVar;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * @author re-xyr
 * @apiNote in each file's dependency tree there should be one and only one EmptyContext which is also the tree root.
 * @implNote EmptyContext is the context storing the underlying file, and its Reporter in the resolving stage.
 */
public record EmptyContext(
  @NotNull Reporter reporter,
  @NotNull Path underlyingFile
) implements Context {
  @Override public @Nullable Context parent() {
    return null;
  }

  @Override public @Nullable AnyVar getUnqualifiedLocalMaybe(
    @NotNull String name,
    @NotNull SourcePos sourcePos
  ) {
    return null;
  }

  @Override
  public @Nullable AnyVar getQualifiedLocalMaybe(
    @NotNull ModulePath.Qualified modName,
    @NotNull String name,
    @NotNull SourcePos sourcePos
  ) {
    return null;
  }

  @Override public @NotNull PhysicalModuleContext derive(@NotNull ImmutableSeq<@NotNull String> extraName) {
    return new PhysicalModuleContext(this, extraName);
  }

  @Override public @NotNull ImmutableSeq<String> moduleName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public @Nullable ModuleExport getModuleLocalMaybe(@NotNull ModulePath.Qualified modName) {
    return null;
  }
}
