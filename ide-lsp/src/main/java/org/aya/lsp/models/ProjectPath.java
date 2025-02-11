// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.models;

import org.aya.generic.Constants;
import org.aya.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

public sealed interface ProjectPath {
  /// Resolve {@param path} to:
  /// * {@link Project}, which represents an aya project ("aya.json" exists)
  /// * {@link Directory}, which represents a normal directory (lack of "aya.json")
  /// * {@link File}, which represents an aya file (with ".aya" pr ".aya.md" extension)
  /// * null, which represents a normal file (unknown file type)
  static @Nullable ProjectPath resolve(@NotNull Path path) {
    path = FileUtil.canonicalize(path);
    if (Files.isDirectory(path)) {
      if (Files.exists(path.resolve(Constants.AYA_JSON))) {
        return new Project(path);
      } else {
        return new Directory(path);
      }
    } else {
      var fileName = path.getFileName().toString();
      if (fileName.equals(Constants.AYA_JSON)) {
        return new Project(path.getParent());
        // ^ never null, a file must belong to some directory
      } else if (fileName.endsWith(Constants.AYA_POSTFIX) || fileName.endsWith(Constants.AYA_LITERATE_POSTFIX)) {
        return new File(path);
      } else {
        return null;
      }
    }
  }

  /// @return canonicalized path
  @NotNull Path path();

  record Project(@Override @NotNull Path path) implements ProjectPath {
    public @NotNull Path ayaJsonPath() { return path.resolve(Constants.AYA_JSON); }
  }

  record Directory(@Override @NotNull Path path) implements ProjectPath {
  }

  record File(@Override @NotNull Path path) implements ProjectPath { }
}
