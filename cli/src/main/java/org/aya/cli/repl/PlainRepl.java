// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Scanner;

public class PlainRepl extends AbstractRepl {
  private final @NotNull Scanner scanner = new Scanner(System.in);

  @Override String readLine() {
    System.out.print(prompt);
    System.out.flush();
    return scanner.nextLine();
  }

  @Override void println(@NotNull String x) {
    System.out.println(x);
  }

  @Override void errPrintln(@NotNull String x) {
    System.err.println(x);
  }

  @Override @Nullable String getAdditionalMessage() {
    return "Note: You are using the plain REPL. Some features may not be available.";
  }

  @Override public void close() throws IOException {
    super.close();
    scanner.close();
  }
}
