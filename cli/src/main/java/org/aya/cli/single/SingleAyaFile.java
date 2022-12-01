// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.single;

import org.aya.concrete.GenericAyaFile;
import org.aya.generic.Constants;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourceFileLocator;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SingleAyaFile {
  public static final class Factory implements GenericAyaFile.Factory {
    @Override public @NotNull GenericAyaFile createAyaFile(@NotNull SourceFileLocator locator, @NotNull Path path) {
      var fileName = path.getFileName().toString();
      if (fileName.endsWith(Constants.AYA_LITERATE_POSTFIX)) return new MarkdownAyaFile(locator, path);
      return new CodeAyaFile(locator, path);
    }
  }

  public record CodeAyaFile(@NotNull SourceFileLocator locator, @NotNull Path path) implements GenericAyaFile {
    @Override public @NotNull SourceFile toSourceFile() throws IOException {
      return new SourceFile(locator.displayName(path).toString(), path, Files.readString(path));
    }
  }

  public record MarkdownAyaFile(@NotNull SourceFileLocator locator, @NotNull Path path) implements GenericAyaFile {
    @Override public @NotNull SourceFile toSourceFile() throws IOException {
      return null; // TODO: implement
    }
  }
}
