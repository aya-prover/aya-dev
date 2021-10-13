// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli.repl;

import org.aya.cli.utils.MainArgs;

import java.io.IOException;

public class Repl {
  public static void run(MainArgs.ReplType replType) throws IOException {
    try (var repl = switch (replType) {
      case plain -> new PlainRepl();
      case jline -> new JlineRepl();
    }) {
      repl.run();
    }
  }
}
