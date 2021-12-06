// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.jline;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.antlr.v4.runtime.Token;
import org.aya.api.util.AyaHome;
import org.aya.cli.repl.AyaRepl;
import org.aya.cli.repl.ReplConfig;
import org.aya.concrete.parse.AyaParsing;
import org.aya.distill.BaseDistiller;
import org.aya.parser.GeneratedLexerTokens;
import org.aya.pretty.backend.string.StringPrinterConfig;
import org.aya.pretty.doc.Doc;
import org.aya.repl.CmdCompleter;
import org.aya.repl.ReplUtil;
import org.aya.repl.antlr.AntlrLexer;
import org.aya.repl.antlr.ReplHighlighter;
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

import java.io.IOException;
import java.nio.file.Path;

public final class JlineRepl extends AyaRepl implements AntlrLexer {
  private final @NotNull Terminal terminal;
  @VisibleForTesting
  public final @NotNull LineReader lineReader;

  public JlineRepl(@NotNull ImmutableSeq<Path> modulePaths, @NotNull ReplConfig config) throws IOException {
    super(modulePaths, config);
    terminal = TerminalBuilder.builder()
      .jansi(true)
      .jna(false)
      .build();
    lineReader = LineReaderBuilder.builder()
      .appName("Aya REPL")
      .terminal(terminal)
      .history(new DefaultHistory())
      .parser(new ReplParser(commandManager, this))
      .highlighter(new ReplHighlighter(this) {
        @Override protected @NotNull Doc highlight(@NotNull Token t) {
          return GeneratedLexerTokens.KEYWORDS.containsKey(t.getType())
            ? Doc.styled(BaseDistiller.KEYWORD, t.getText())
            : Doc.plain(t.getText());
        }
      })
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

  @Override @NotNull public String readLine(@NotNull String prompt)
    throws EndOfFileException, UserInterruptException {
    return lineReader.readLine(prompt);
  }

  @Override public @NotNull String renderDoc(@NotNull Doc doc) {
    return doc.renderToString(StringPrinterConfig.unixTerminal(prettyPrintWidth, config.enableUnicode));
  }

  @Override public void println(@NotNull String x) {
    if (terminal instanceof AbstractWindowsTerminal) terminal.writer().println(AttributedString.fromAnsi(x));
    else terminal.writer().println(x);
    terminal.flush();
  }

  // see `eprintln` in https://github.com/JetBrains/Arend/blob/master/cli/src/main/java/org/arend/frontend/repl/jline/JLineCliRepl.java
  @Override public void errPrintln(@NotNull String x) {
    println(ReplUtil.red(x));
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
