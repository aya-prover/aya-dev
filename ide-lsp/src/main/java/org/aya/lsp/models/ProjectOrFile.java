// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.models;

import org.aya.generic.Constants;
import org.aya.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;

public sealed interface ProjectOrFile {
  static @NotNull ProjectOrFile resolve(@NotNull Path path) {
    path = FileUtil.canonicalize(path);
    if (Files.isDirectory(path)) {
      return new Project(path);
    } else {
      var fileName = path.getFileName().toString();
      if (fileName.equals(Constants.AYA_JSON)) {
        return new Project(path.getParent());
        // ^ never null, a file must belong to some directory
      } else if (fileName.endsWith(Constants.AYA_POSTFIX) || fileName.endsWith(Constants.AYA_LITERATE_POSTFIX)) {
        return new File(path);
      }
    }
  }

  /// @return canonicalized path
  @NotNull Path path();

  record Project(@Override @NotNull Path path) implements ProjectOrFile {
    public @NotNull Path ayaJsonPath() { return path.resolve(Constants.AYA_JSON); }
  }

  record File(@Override @NotNull Path path) implements ProjectOrFile { }
}
