// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli.repl;

import org.aya.cli.repl.jline.AyaParser;
import org.jetbrains.annotations.Nullable;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.io.IOException;

public class JlineRepl extends AbstractRepl {
  Terminal terminal;
  LineReader lineReader;

  public JlineRepl() throws IOException {
    terminal = TerminalBuilder.builder()
      .system(true)
      .build();

    lineReader = LineReaderBuilder.builder()
      .terminal(terminal)
      .parser(new AyaParser())
      .build();
  }

  @Override
  String readLine(String prompt) {
    return lineReader.readLine(prompt);
  }

  @Override
  void println(String x) {
    terminal.writer().println(x);
    terminal.flush();
  }

  // see `eprintln` in https://github.com/JetBrains/Arend/blob/master/cli/src/main/java/org/arend/frontend/repl/jline/JLineCliRepl.java
  @Override
  void errPrintln(String x) {
    println(new AttributedStringBuilder()
      .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED))
      .append(String.valueOf(x))
      .style(AttributedStyle.DEFAULT)
      .toAnsi());
  }

  @Override
  @Nullable String getAdditionalMessage() {
    return null;
  }

  @Override
  public void close() throws IOException {
    terminal.close();
  }
}
