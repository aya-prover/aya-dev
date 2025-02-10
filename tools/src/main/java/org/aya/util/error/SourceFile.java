// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.error;

import com.intellij.openapi.util.text.Strings;
import kala.collection.immutable.primitive.ImmutableIntArray;
import kala.control.Option;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Unified source file representation for error reporting only.
 *
 * @param display Usually constructed with {@link SourceFileLocator#displayName(Path)}
 */
public record SourceFile(
  @NotNull String display,
  @NotNull Option<Path> underlying,
  @NotNull String sourceCode,
  @NotNull ImmutableIntArray lineOffsets
) {
  public static @NotNull SourceFile from(@NotNull SourceFileLocator locator, @NotNull Path path) throws IOException {
    return from(locator, path, Files.readString(path));
  }

  public static @NotNull SourceFile from(@NotNull SourceFileLocator locator, @NotNull Path path, @NotNull String sourceCode) {
    return new SourceFile(locator.displayName(path).toString(), path, sourceCode);
  }

  public SourceFile(@NotNull String display, @NotNull Option<Path> underlying, @NotNull String sourceCode) {
    this(display, underlying, Strings.convertLineSeparators(sourceCode), ParsingUtil.indexedLines(sourceCode));
  }

  public SourceFile(@NotNull String display, @NotNull Path underlying, @NotNull String sourceCode) {
    this(display, Option.some(underlying), Strings.convertLineSeparators(sourceCode));
  }

  public static final SourceFile NONE =
    new SourceFile("<unknown-file>", Option.none(), "", ImmutableIntArray.empty());
  public static final SourceFile SER =
    new SourceFile("<serialized-core>", Option.none(), "", ImmutableIntArray.empty());

  public boolean isSomeFile() {
    return underlying.isDefined();
  }

  public @NotNull Path resolveSibling(@NotNull Path sibling) {
    return underlying().getOrElse(() -> Path.of(".")).resolveSibling(sibling);
  }
}
