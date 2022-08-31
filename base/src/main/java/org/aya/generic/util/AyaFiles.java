// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic.util;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.Constants;
import org.aya.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public interface AyaFiles {
  static @NotNull String stripAyaSourcePostfix(@NotNull String name) {
    return Constants.AYA_POSTFIX_PATTERN.matcher(name).replaceAll("");
  }

  static @NotNull Path resolveAyaSourceFile(@NotNull Path basePath, @NotNull ImmutableSeq<String> moduleName) {
    // TODO: resolve literate aya file
    return FileUtil.resolveFile(basePath, moduleName, Constants.AYA_POSTFIX);
  }
  static @Nullable Path resolveAyaSourceFile(@NotNull SeqView<Path> basePaths, @NotNull ImmutableSeq<String> moduleName) {
    // TODO: resolve literate aya file
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
    // TODO: collect literate aya file
    return FileUtil.collectSource(basePath, Constants.AYA_POSTFIX, maxDepth);
  }
}
