// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp;

import picocli.CommandLine;

public class LspMain {
  public static void main(String[] args) {
    int exitCode = new CommandLine(new Lsp()).execute(args);
    System.exit(exitCode);
  }
}
