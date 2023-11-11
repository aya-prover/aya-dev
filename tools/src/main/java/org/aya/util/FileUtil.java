// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public interface FileUtil {
  static void writeString(@NotNull Path path, @NotNull String content) throws IOException {
    var parent = path.toAbsolutePath().getParent();
    if (parent != null && Files.notExists(parent)) Files.createDirectories(parent);
    // No need to set UTF_8 since Java 17
    Files.writeString(path, content);
  }

  static @NotNull String escapeFileName(@NotNull String s) {
    // Escape file names, see https://stackoverflow.com/a/41108758/7083401
    return s.replaceAll("[\\\\/:*?\"<>|]", "_");
  }

  static @NotNull Path canonicalize(@NotNull Path path) {
    try {
      return path.toRealPath();
    } catch (IOException ignored) {
      return path.toAbsolutePath().normalize();
    }
  }

  static void deleteRecursively(@NotNull Path path) throws IOException {
    if (!Files.exists(path)) return;
    try (var walk = Files.walk(path)) {
      walk.sorted(Comparator.reverseOrder())
        .collect(ImmutableSeq.factory())
        .forEachChecked(Files::deleteIfExists);
    }
  }

  static @NotNull ImmutableSeq<Path> collectSource(@NotNull Path srcRoot, @NotNull ImmutableSeq<String> postfix, int maxDepth) {
    try (var walk = Files.walk(srcRoot, maxDepth)) {
      return walk
        .filter(Files::isRegularFile)
        .filter(path -> {
          var name = path.getFileName().toString();
          return postfix.anyMatch(name::endsWith);
        })
        .collect(ImmutableSeq.factory());
    } catch (IOException e) {
      return ImmutableSeq.empty();
    }
  }

  static @NotNull ObjectInputStream ois(@NotNull Path corePath) throws IOException {
    return new ObjectInputStream(Files.newInputStream(corePath));
  }

  static @NotNull Path resolveFile(@NotNull Path basePath, @NotNull Seq<@NotNull String> moduleName, String postfix) {
    var withoutExt = moduleName.foldLeft(basePath, Path::resolve);
    return withoutExt.resolveSibling(withoutExt.getFileName() + postfix);
  }

  static @Nullable Path resolveFile(@NotNull SeqView<Path> basePaths, @NotNull Seq<String> moduleName, String postfix) {
    return basePaths.map(basePath -> resolveFile(basePath, moduleName, postfix))
      .findFirst(Files::exists).getOrNull();
  }
}
