// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.repl;

import org.jetbrains.annotations.NotNull;

import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Scanner;

/**
 * A simple wrapper for common IO operations.
 */
public record IO(
  @NotNull Scanner scanner,
  @NotNull PrintWriter out,
  @NotNull PrintWriter err
) {
  public IO(@NotNull Readable input, @NotNull Writer out, @NotNull Writer err) {
    this(new Scanner(input), new PrintWriter(out), new PrintWriter(err));
  }

  public static final @NotNull IO STDIO = new IO(new InputStreamReader(System.in), new PrintWriter(System.out), new PrintWriter(System.err));

  public @NotNull String readLine(@NotNull String prompt) {
    out.print(prompt);
    out.flush();
    return scanner.nextLine();
  }
}
