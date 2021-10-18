// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import org.aya.cli.repl.jline.JlineRepl;
import org.aya.cli.repl.plain.PlainRepl;
import org.aya.cli.utils.MainArgs;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class ReplFactory {
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
