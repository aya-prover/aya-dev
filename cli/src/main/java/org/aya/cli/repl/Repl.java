// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import org.aya.cli.utils.MainArgs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Repl {
  private static Path CONFIG_ROOT;

  static @Nullable Path configRoot() {
    if (CONFIG_ROOT == null) {
      String ayaHome = System.getenv("AYA_HOME");
      CONFIG_ROOT = ayaHome == null ? Paths.get(System.getProperty("user.home"), ".aya") : Paths.get(ayaHome);
    }
    try {
      Files.createDirectories(CONFIG_ROOT);
    } catch (IOException ignored) {
      CONFIG_ROOT = null;
    }
    return CONFIG_ROOT;
  }

  static {
  }

  public static int run(MainArgs.@NotNull ReplAction replAction) throws IOException {
    try (var repl = switch (replAction.replType) {
      case jline -> new JlineRepl();
      case plain -> new PlainRepl();
    }) {
      repl.run();
    }
    return 0;
  }
}
