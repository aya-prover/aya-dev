// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library;

import org.aya.api.error.SourceFileLocator;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record Timestamp(@NotNull SourceFileLocator locator, @NotNull Path outRoot) {
  public boolean sourceModified(@NotNull Path file) {
    try {
      var core = LibraryCompiler.coreFile(locator, file, outRoot);
      if (!Files.exists(core)) return true;
      return Files.getLastModifiedTime(file)
        .compareTo(Files.getLastModifiedTime(core)) > 0;
    } catch (IOException ignore) {
      return true;
    }
  }

  public void update(@NotNull Path file) {
    try {
      var core = LibraryCompiler.coreFile(locator, file, outRoot);
      Files.setLastModifiedTime(core, Files.getLastModifiedTime(file));
    } catch (IOException ignore) {
    }
  }
}
