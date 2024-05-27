// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.Constants;
import org.aya.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

// TODO: really in syntax?
public interface AyaFiles {
  @NotNull ImmutableSeq<String> AYA_SOURCE_POSTFIXES = ImmutableSeq.of(Constants.AYA_POSTFIX, Constants.AYA_LITERATE_POSTFIX);

  static @NotNull String stripAyaSourcePostfix(@NotNull String name) {
    return Constants.AYA_POSTFIX_PATTERN.matcher(name).replaceAll("");
  }

  static boolean isLiterate(@NotNull Path path) {
    return path.getFileName().toString().endsWith(Constants.AYA_LITERATE_POSTFIX);
  }

  static @NotNull Path resolveAyaSourceFile(@NotNull Path basePath, @NotNull ImmutableSeq<String> moduleName) {
    var literate = FileUtil.resolveFile(basePath, moduleName, Constants.AYA_LITERATE_POSTFIX);
    if (Files.exists(literate)) return literate;
    return FileUtil.resolveFile(basePath, moduleName, Constants.AYA_POSTFIX);
  }
  static @Nullable Path resolveAyaSourceFile(@NotNull SeqView<Path> basePaths, @NotNull ImmutableSeq<String> moduleName) {
    var literate = FileUtil.resolveFile(basePaths, moduleName, Constants.AYA_LITERATE_POSTFIX);
    if (literate != null) return literate;
    return FileUtil.resolveFile(basePaths, moduleName, Constants.AYA_POSTFIX);
  }
  static @NotNull Path resolveAyaCompiledFile(@NotNull Path basePath, @NotNull ImmutableSeq<String> moduleName) {
    return FileUtil.resolveFile(basePath, moduleName, Constants.AYAC_POSTFIX);
  }
  static @Nullable Path resolveAyaCompiledFile(@NotNull SeqView<Path> basePaths, @NotNull ImmutableSeq<String> moduleName) {
    return FileUtil.resolveFile(basePaths, moduleName, Constants.AYAC_POSTFIX);
  }

  static @NotNull ImmutableSeq<Path> collectAyaSourceFiles(@NotNull Path basePath) {
    return collectAyaSourceFiles(basePath, Integer.MAX_VALUE);
  }
  static @NotNull ImmutableSeq<Path> collectAyaSourceFiles(@NotNull Path basePath, int maxDepth) {
    return FileUtil.collectSource(basePath, AYA_SOURCE_POSTFIXES, maxDepth);
  }
}
