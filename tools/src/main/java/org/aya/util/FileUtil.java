// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public interface FileUtil {
  static void deleteRecursively(@NotNull Path path) throws IOException {
    if (!Files.exists(path)) return;
    try (var walk = Files.walk(path)) {
      walk.sorted(Comparator.reverseOrder())
        .collect(ImmutableSeq.factory())
        .forEachChecked(Files::deleteIfExists);
    }
  }

  static @NotNull ImmutableSeq<Path> collectSource(@NotNull Path srcRoot, @NotNull String postfix) {
    try (var walk = Files.walk(srcRoot)) {
      return walk.filter(Files::isRegularFile)
        .filter(path -> path.getFileName().toString().endsWith(postfix))
        .collect(ImmutableSeq.factory());
    } catch (IOException e) {
      return ImmutableSeq.empty();
    }
  }

  static @NotNull ObjectInputStream ois(@NotNull Path corePath) throws IOException {
    return new ObjectInputStream(Files.newInputStream(corePath));
  }
}
