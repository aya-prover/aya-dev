// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.jline;

import com.intellij.lexer.FlexLexer;
import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.interactive.ReplConfig;
import org.aya.cli.render.RenderOptions;
import org.aya.cli.repl.AyaRepl;
import org.aya.cli.repl.gk.GKReplLexer;
import org.aya.generic.AyaHome;
import org.aya.parser.AyaParserDefinitionBase;
import org.aya.prettier.BasePrettier;
import org.aya.pretty.backend.string.StringPrinterConfig;
import org.aya.pretty.doc.Doc;
import org.aya.repl.CmdCompleter;
import org.aya.repl.ReplHighlighter;
import org.aya.repl.ReplParser;
import org.aya.repl.ReplUtil;
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
import org.jline.terminal.impl.DumbTerminal;

import java.io.IOException;
import java.nio.file.Path;

public final class JlineRepl extends AyaRepl {
  private final @NotNull Terminal terminal;
  @VisibleForTesting
  public final @NotNull LineReader lineReader;

  // Needs to be instance member because it's stateful
  public final @NotNull GKReplLexer lexer = new GKReplLexer(AyaParserDefinitionBase.createLexer(true));

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
      .parser(new ReplParser<>(commandManager, lexer))
      .highlighter(new ReplHighlighter<>(lexer) {
        @Override protected @NotNull Doc highlight(String text, @NotNull FlexLexer.Token t) {
          return AyaParserDefinitionBase.KEYWORDS.contains(t.type())
            ? Doc.styled(BasePrettier.KEYWORD, text)
            : Doc.plain(text);
        }

        @Override protected @NotNull String renderToTerminal(@NotNull Doc doc) {
          return renderDoc(doc, StringPrinterConfig.INFINITE_SIZE);
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

  private int widthOf(@NotNull Terminal terminal) {
    if (terminal instanceof DumbTerminal) return 80;
    return terminal.getWidth();
  }

  @Override @NotNull public String readLine(@NotNull String prompt)
    throws EndOfFileException, UserInterruptException {
    return lineReader.readLine(prompt);
  }

  @Override public @NotNull String renderDoc(@NotNull Doc doc) {
    return renderDoc(doc, prettyPrintWidth);
  }

  private @NotNull String renderDoc(@NotNull Doc doc, int pageWidth) {
    return config.literatePrettier.renderOptions.render(RenderOptions.OutputTarget.Unix, doc,
      new RenderOptions.DefaultSetup(false, false, false, config.enableUnicode, pageWidth, false));
  }

  @Override public void println(@NotNull String x) {
    terminal.writer().println(x);
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
