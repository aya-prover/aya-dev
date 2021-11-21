// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import org.aya.api.error.SourceFileLocator;
import org.aya.concrete.resolve.ResolveInfo;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.IntStream;

public record LibrarySource(
  @NotNull LibraryCompiler owner,
  @NotNull Path file,
  @NotNull DynamicSeq<LibrarySource> imports
) {
  public LibrarySource(@NotNull LibraryCompiler owner, @NotNull Path file) {
    this(owner, ResolveInfo.canonicalize(file), DynamicSeq.create());
  }

  public @NotNull ImmutableSeq<String> moduleName() {
    return moduleName(owner.locator, file);
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LibrarySource that = (LibrarySource) o;
    return owner.library == that.owner.library && file.equals(that.file);
  }

  @Override public int hashCode() {
    return Objects.hash(owner, file);
  }

  private static @NotNull ImmutableSeq<String> moduleName(@NotNull SourceFileLocator locator, @NotNull Path file) {
    var display = locator.displayName(file);
    var displayNoExt = display.resolveSibling(display.getFileName().toString().replaceAll("\\.aya", ""));
    return IntStream.range(0, displayNoExt.getNameCount())
      .mapToObj(i -> displayNoExt.getName(i).toString())
      .collect(ImmutableSeq.factory());
  }
}
