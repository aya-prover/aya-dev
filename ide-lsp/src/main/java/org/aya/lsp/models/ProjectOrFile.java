// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.models;

import org.aya.generic.Constants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

public sealed interface ProjectOrFile {
  /// @return null if {@param path} is neither represents an aya project nor an aya file
  static @Nullable ProjectOrFile resolve(@NotNull Path path) {
    if (Files.isDirectory(path)) {
      if (Files.exists(path.resolve(Constants.AYA_JSON))) {
        return new Project(path);
      }
    } else {
      var fileName = path.getFileName().toString();
      if (fileName.equals(Constants.AYA_JSON)) {
        return new Project(path.getParent());     // never null, a file must belongs to some directory
      } else if (fileName.endsWith(Constants.AYA_POSTFIX) || fileName.endsWith(Constants.AYA_LITERATE_POSTFIX)) {
        return new File(path);
      }
    }

    return null;
  }

  @NotNull Path path();

  record Project(@NotNull Path path) implements ProjectOrFile {
    public @NotNull Path ayaJsonPath() {
      return path.resolve(Constants.AYA_JSON);
    }
  }

  record File(@NotNull Path path) implements ProjectOrFile { }
}
