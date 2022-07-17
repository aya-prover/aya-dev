// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library.source;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.value.Ref;
import org.aya.concrete.stmt.Stmt;
import org.aya.core.def.GenericDef;
import org.aya.generic.Constants;
import org.aya.resolve.ResolveInfo;
import org.aya.util.FileUtil;
import org.aya.util.error.SourceFile;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * A source file may or may not be tycked.
 *
 * @param program     initialized after parse
 * @param resolveInfo initialized after resolve
 * @param tycked      initialized after tyck
 * @apiNote DO NOT USE this class as Map key. Use {@link #file()} instead.
 */
@Debug.Renderer(text = "file")
public record LibrarySource(
  @NotNull LibraryOwner owner,
  @NotNull Path file,
  @NotNull MutableList<LibrarySource> imports,
  @NotNull Ref<ImmutableSeq<Stmt>> program,
  @NotNull Ref<ImmutableSeq<GenericDef>> tycked,
  @NotNull Ref<ResolveInfo> resolveInfo
) {
  public LibrarySource(@NotNull LibraryOwner owner, @NotNull Path file) {
    this(owner, FileUtil.canonicalize(file), MutableList.create(), new Ref<>(), new Ref<>(), new Ref<>());
  }

  public @NotNull ImmutableSeq<String> moduleName() {
    if (resolveInfo.value != null) return resolveInfo.value.thisModule().moduleName();
    var display = displayPath();
    var displayNoExt = display.resolveSibling(display.getFileName().toString().replaceAll("\\.aya", ""));
    return IntStream.range(0, displayNoExt.getNameCount())
      .mapToObj(i -> displayNoExt.getName(i).toString())
      .collect(ImmutableSeq.factory());
  }

  public @NotNull Path displayPath() {
    return owner.locator().displayName(file);
  }

  public @NotNull SourceFile toSourceFile(@NotNull String sourceCode) {
    return new SourceFile(displayPath().toString(), file, sourceCode);
  }

  public @NotNull Path compiledCorePath() {
    var mod = moduleName();
    return FileUtil.resolveFile(owner.outDir(), mod, Constants.AYAC_POSTFIX);
  }

  @Override public String toString() {
    return file.toString();
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LibrarySource that = (LibrarySource) o;
    return owner.underlyingLibrary() == that.owner.underlyingLibrary() && file.equals(that.file);
  }

  @Override public int hashCode() {
    return Objects.hash(owner.underlyingLibrary(), file);
  }
}
