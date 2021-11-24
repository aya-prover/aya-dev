// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.value.Ref;
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.stmt.Stmt;
import org.aya.generic.Constants;
import org.aya.util.FileUtil;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * A source file may or may not be tycked.
 *
 * @param program     initialized after parse
 * @param resolveInfo initialized after resolve
 */
@Debug.Renderer(text = "file")
public record LibrarySource(
  @NotNull LibraryCompiler owner,
  @NotNull Path file,
  @NotNull DynamicSeq<LibrarySource> imports,
  @NotNull Ref<ImmutableSeq<Stmt>> program,
  @NotNull Ref<ResolveInfo> resolveInfo
) {
  public LibrarySource(@NotNull LibraryCompiler owner, @NotNull Path file) {
    this(owner, canonicalize(file), DynamicSeq.create(), new Ref<>(), new Ref<>());
  }

  public @NotNull ImmutableSeq<String> moduleName() {
    var display = displayPath();
    var displayNoExt = display.resolveSibling(display.getFileName().toString().replaceAll("\\.aya", ""));
    return IntStream.range(0, displayNoExt.getNameCount())
      .mapToObj(i -> displayNoExt.getName(i).toString())
      .collect(ImmutableSeq.factory());
  }

  public @NotNull Path displayPath() {
    return owner.locator.displayName(file);
  }

  public @NotNull Path coreFile() {
    var mod = moduleName();
    return FileUtil.resolveFile(owner.library.libraryOutRoot(), mod, Constants.AYAC_POSTFIX);
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
