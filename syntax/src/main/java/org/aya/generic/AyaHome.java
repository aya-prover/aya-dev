// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AyaHome {
  private static Path AYA_HOME;

  public static @NotNull Path ayaHome() throws IOException {
    if (AYA_HOME == null) {
      String ayaHome = System.getenv("AYA_HOME");
      AYA_HOME = ayaHome != null ? Paths.get(ayaHome) : Paths.get(System.getProperty("user.home"), ".aya");
    }
    return Files.createDirectories(AYA_HOME);
  }
}
