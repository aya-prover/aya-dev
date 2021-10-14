// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import org.aya.cli.utils.MainArgs;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class Repl {
  public static int run(MainArgs.@NotNull ReplAction replAction) throws IOException {
    try (var repl = createRepl(replAction.replType)) {
      repl.run();
    }
    return 0;
  }

  private static @NotNull AbstractRepl createRepl(MainArgs.@NotNull ReplType replType) throws IOException {
    return switch (replType) {
      case jline -> new JlineRepl();
      case plain -> new PlainRepl();
    };
  }
}
