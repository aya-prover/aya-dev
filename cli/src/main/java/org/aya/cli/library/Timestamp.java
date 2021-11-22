// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;

public class Timestamp {
  public static boolean sourceModified(@NotNull LibrarySource file) {
    try {
      var core = file.coreFile();
      if (!Files.exists(core)) return true;
      return Files.getLastModifiedTime(file.file())
        .compareTo(Files.getLastModifiedTime(core)) > 0;
    } catch (IOException ignore) {
      return true;
    }
  }

  public static void update(@NotNull LibrarySource file) {
    try {
      var core = file.coreFile();
      Files.setLastModifiedTime(core, Files.getLastModifiedTime(file.file()));
    } catch (IOException ignore) {
    }
  }
}
