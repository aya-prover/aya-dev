// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.jline;

import kala.collection.SeqView;
import org.antlr.v4.runtime.Token;
import org.aya.api.util.AyaHome;
import org.aya.cli.repl.Repl;
import org.aya.cli.repl.ReplConfig;
import org.aya.concrete.parse.AyaParsing;
import org.aya.pretty.backend.string.StringPrinterConfig;
import org.aya.pretty.doc.Doc;
import org.aya.repl.CmdCompleter;
import org.aya.repl.antlr.AntlrLexer;
import org.aya.repl.antlr.ReplParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.completer.AggregateCompleter;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.impl.AbstractWindowsTerminal;
import org.jline.terminal.impl.DumbTerminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.io.EOFException;
import java.io.IOException;

public final class JlineRepl extends Repl implements AntlrLexer {
  private final @NotNull Terminal terminal;
  @VisibleForTesting
  public final @NotNull LineReader lineReader;

  public JlineRepl(@NotNull ReplConfig config) throws IOException {
    super(config);
    terminal = TerminalBuilder.builder()
      .jansi(true)
      .jna(false)
      .build();
    lineReader = LineReaderBuilder.builder()
      .appName("Aya REPL")
      .terminal(terminal)
      .history(new DefaultHistory())
      .parser(new ReplParser(commandManager, this))
      .highlighter(new AyaReplHighlighter(this))
      .completer(new AggregateCompleter(
        new CmdCompleter(commandManager, new AyaCompleters.Code(this))
      ))
      .variable(LineReader.HISTORY_FILE, AyaHome.ayaHome().resolve("history"))
      .variable(LineReader.SECONDARY_PROMPT_PATTERN, "| ")
      .build();
    prettyPrintWidth = widthOf(terminal);
    terminal.handle(Terminal.Signal.WINCH, signal -> prettyPrintWidth = widthOf(terminal));
  }

  @Override public @NotNull SeqView<Token> tokensNoEOF(String line) {
    return AyaParsing.tokens(line).view().dropLast(1);
  }

  private int widthOf(@NotNull Terminal terminal) {
    if (terminal instanceof DumbTerminal) return 80;
    return terminal.getWidth();
  }

  @Override protected @NotNull String readLine(@NotNull String prompt) throws EOFException, InterruptedException {
    try {
      return lineReader.readLine(prompt);
    } catch (EndOfFileException ignored) {
      throw new EOFException();
    } catch (UserInterruptException ignored) {
      throw new InterruptedException();
    }
  }

  @Override public @NotNull String renderDoc(@NotNull Doc doc) {
    return doc.renderToString(StringPrinterConfig.unixTerminal(prettyPrintWidth, config.enableUnicode));
  }

  @Override protected void println(@NotNull String x) {
    if (terminal instanceof AbstractWindowsTerminal) terminal.writer().println(AttributedString.fromAnsi(x));
    else terminal.writer().println(x);
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
