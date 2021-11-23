// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
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
}
