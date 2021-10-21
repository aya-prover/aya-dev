// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.jline;

import org.aya.cli.repl.AbstractRepl;
import org.aya.cli.repl.jline.completer.KeywordCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.completer.AggregateCompleter;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.io.IOException;

public final class JlineRepl extends AbstractRepl {
  private final @NotNull Terminal terminal;
  private final @NotNull LineReader lineReader;

  public JlineRepl() throws IOException {
    terminal = TerminalBuilder.builder()
      .jansi(true)
      .jna(false)
      .build();
    var lineReaderBuilder = LineReaderBuilder.builder()
      .appName(APP_NAME)
      .terminal(terminal)
      // .parser(new AyaParser())
      .completer(new AggregateCompleter(
        KeywordCompleter.INSTANCE,
        commandExecutor.completer()
      ));
    var root = configRoot();
    if (root != null) lineReaderBuilder
      .variable("history-file", root.resolve("history"))
      .history(new DefaultHistory());
    lineReader = lineReaderBuilder.build();
  }

  @Override protected @NotNull String readLine() {
    return lineReader.readLine(prompt);
  }

  @Override protected void println(@NotNull String x) {
    terminal.writer().println(x);
    terminal.flush();
  }

  // see `eprintln` in https://github.com/JetBrains/Arend/blob/master/cli/src/main/java/org/arend/frontend/repl/jline/JLineCliRepl.java
  @Override protected void errPrintln(@NotNull String x) {
    println(new AttributedStringBuilder()
      .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED))
      .append(x)
      .style(AttributedStyle.DEFAULT)
      .toAnsi());
  }

  @Override protected @Nullable String getAdditionalMessage() {
    return null;
  }

  @Override public void close() throws IOException {
    super.close();
    terminal.close();
    lineReader.getHistory().save();
  }
}
