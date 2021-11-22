// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.IntStream;

@Debug.Renderer(text = "file")
public record LibrarySource(
  @NotNull LibraryCompiler owner,
  @NotNull Path file,
  @NotNull DynamicSeq<LibrarySource> imports
) {
  public LibrarySource(@NotNull LibraryCompiler owner, @NotNull Path file) {
    this(owner, canonicalize(file), DynamicSeq.create());
  }

  public @NotNull ImmutableSeq<String> moduleName() {
    var display = owner.locator.displayName(file);
    var displayNoExt = display.resolveSibling(display.getFileName().toString().replaceAll("\\.aya", ""));
    return IntStream.range(0, displayNoExt.getNameCount())
      .mapToObj(i -> displayNoExt.getName(i).toString())
      .collect(ImmutableSeq.factory());
  }

  public @NotNull Path coreFile() {
    var mod = moduleName();
    return LibraryModuleLoader.resolveCompiledCore(owner.library.libraryOutRoot(), mod);
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LibrarySource that = (LibrarySource) o;
    return owner.library == that.owner.library && file.equals(that.file);
  }

  @Override public int hashCode() {
    return Objects.hash(owner.library, file);
  }

  public static @NotNull Path canonicalize(@NotNull Path path) {
    try {
      return path.toRealPath();
    } catch (IOException ignored) {
      return path.toAbsolutePath().normalize();
    }
  }
}
