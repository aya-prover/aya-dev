// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.jline;

import org.aya.api.util.AyaHome;
import org.aya.cli.repl.Repl;
import org.aya.cli.repl.ReplConfig;
import org.aya.cli.repl.jline.completer.KeywordCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.completer.AggregateCompleter;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.io.EOFException;
import java.io.IOException;

public final class JlineRepl extends Repl {
  private final @NotNull Terminal terminal;
  private final @NotNull LineReader lineReader;

  public JlineRepl(@NotNull ReplConfig config) throws IOException {
    super(config);
    terminal = TerminalBuilder.builder()
      .jansi(true)
      .jna(false)
      .build();
    lineReader = LineReaderBuilder.builder()
      .appName("Aya REPL")
      .terminal(terminal)
      // .parser(new AyaParser())
      .completer(new AggregateCompleter(
        KeywordCompleter.INSTANCE,
        commandManager.completer()
      ))
      .variable("history-file", AyaHome.ayaHome().resolve("history"))
      .history(new DefaultHistory())
      .build();
    prettyPrintWidth = terminal.getWidth();
  }

  @Override protected @NotNull String readLine(@NotNull String prompt) throws EOFException {
    try {
      return lineReader.readLine(prompt);
    } catch (EndOfFileException ignored) {
      throw new EOFException();
    }
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

  @Override protected @Nullable String hintMessage() {
    return null;
  }

  @Override public void close() throws IOException {
    super.close();
    terminal.close();
    lineReader.getHistory().save();
  }
}
