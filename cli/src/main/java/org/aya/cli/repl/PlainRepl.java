// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli.repl;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Scanner;

public class PlainRepl extends AbstractRepl {
  Scanner scanner = new Scanner(System.in);

  @Override
  String readLine(String prompt) {
    System.out.print(prompt);
    System.out.flush();
    return scanner.nextLine();
  }

  @Override
  void println(String x) {
    System.out.println(x);
  }

  @Override
  void errPrintln(String x) {
    System.err.println(x);
  }

  @Override
  @Nullable String getAdditionalMessage() {
    return "Note: You are using the plain REPL. Some features may not be available.";
  }

  @Override
  public void close() throws IOException {
  }
}
