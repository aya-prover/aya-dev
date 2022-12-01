// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete;

import org.aya.util.error.SourceFile;
import org.aya.util.error.SourceFileLocator;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

public interface GenericAyaFile {
  interface Factory {
    @NotNull GenericAyaFile createAyaFile(@NotNull SourceFileLocator locator, @NotNull Path path) throws IOException;
  }

  /** @return The source file that contains only aya source code */
  @NotNull SourceFile toSourceFile() throws IOException;
}
