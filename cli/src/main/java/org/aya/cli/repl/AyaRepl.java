// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.distill.AyaDocile;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.Problem;
import org.aya.api.util.NormalizeMode;
import org.aya.cli.repl.jline.AyaCompleters;
import org.aya.cli.repl.jline.JlineRepl;
import org.aya.cli.single.CliReporter;
import org.aya.cli.utils.MainArgs;
import org.aya.prelude.GeneratedVersion;
import org.aya.pretty.doc.Doc;
import org.aya.repl.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jline.builtins.Completers;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;

public abstract class AyaRepl implements Closeable, Runnable, Repl {
  public static int start(@NotNull ImmutableSeq<Path> modulePaths, MainArgs.@NotNull ReplAction replAction) throws IOException {
    try (var repl = makeRepl(modulePaths, replAction, ReplConfig.loadFromDefault())) {
      repl.run();
    }
    return 0;
  }

  @NotNull
  private static AyaRepl makeRepl(@NotNull ImmutableSeq<Path> modulePaths, MainArgs.@NotNull ReplAction replAction, ReplConfig replConfig) throws IOException {
    return switch (replAction.replType) {
      case jline -> new JlineRepl(modulePaths, replConfig);
      case plain -> new PlainRepl(modulePaths, replConfig, IO.STDIO);
    };
  }

  public CommandManager makeCommand() {
    return new CommandManager(AyaRepl.class, ImmutableSeq.of(
      CommandArg.STRING,
      CommandArg.STRICT_BOOLEAN,
      CommandArg.STRICT_INT,
      CommandArg.shellLike(Path.class, new Completers.FileNameCompleter(), this::resolveFile),
      CommandArg.from(ReplCommands.Code.class, new AyaCompleters.Code(this), ReplCommands.Code::new),
      CommandArg.from(ReplUtil.HelpItem.class, new ReplCompleters.Help(() -> commandManager), ReplUtil.HelpItem::new),
      CommandArg.fromEnum(DistillerOptions.Key.class),
      CommandArg.fromEnum(NormalizeMode.class)
    ), ImmutableSeq.of(
      ReplCommands.HELP,
      ReplCommands.QUIT,
      ReplCommands.CHANGE_PROMPT,
      ReplCommands.CHANGE_NORM_MODE,
      ReplCommands.TOGGLE_DISTILL,
      ReplCommands.SHOW_TYPE,
      ReplCommands.CHANGE_PP_WIDTH,
      ReplCommands.TOGGLE_UNICODE,
      ReplCommands.CHANGE_CWD,
      ReplCommands.PRINT_CWD,
      ReplCommands.LOAD
    ));
  }

  public final @NotNull ReplConfig config;
  public @NotNull Path cwd = Path.of("");
  public int prettyPrintWidth = 80;
  public final @NotNull ReplCompiler replCompiler;
  public final @NotNull CommandManager commandManager = makeCommand();

  public AyaRepl(@NotNull ImmutableSeq<Path> modulePaths, @NotNull ReplConfig config) {
    this.config = config;
    replCompiler = new ReplCompiler(modulePaths, new CliReporter(true,
      () -> config.enableUnicode, () -> config.distillerOptions,
      Problem.Severity.INFO, this::println, this::errPrintln), null);
  }

  protected abstract @Nullable String hintMessage();

  public @NotNull Path resolveFile(@NotNull String arg) {
    return ReplUtil.resolveFile(arg, cwd);
  }

  @Override public void run() {
    println("Aya " + GeneratedVersion.VERSION_STRING + " (" + GeneratedVersion.COMMIT_HASH + ")");
    var hint = hintMessage();
    if (hint != null) println(hint);
    //noinspection StatementWithEmptyBody
    while (singleLoop()) ;
  }

  /**
   * Executes a single REPL loop.
   *
   * @return <code>true</code> if the REPL should continue to receive user input and execute,
   * <code>false</code> if it should quit.
   */
  private boolean singleLoop() {
    replCompiler.reporter.clear();
    return loop(config.prompt, commandManager);
  }

  @Override public @NotNull Command.Output eval(@NotNull String line) {
    var programOrTerm = replCompiler.compileToContext(line, config.normalizeMode);
    return Command.Output.stdout(programOrTerm.fold(
      program -> Doc.vcat(program.view().map(def -> def.toDoc(config.distillerOptions))),
      this::render
    ));
  }

  public @NotNull Doc render(@NotNull AyaDocile ayaDocile) {
    return ayaDocile.toDoc(config.distillerOptions);
  }

  @Override public @NotNull String renderDoc(@NotNull Doc doc) {
    return doc.renderWithPageWidth(prettyPrintWidth, config.enableUnicode);
  }

  @Override public void close() throws IOException {
    config.close();
  }

  /**
   * Default repl when jline is unavailable
   */
  public static class PlainRepl extends AyaRepl {
    private final @NotNull IO io;

    public PlainRepl(@NotNull ImmutableSeq<Path> modulePaths, @NotNull ReplConfig config, @NotNull IO io) {
      super(modulePaths, config);
      this.io = io;
    }

    @Override public @NotNull String readLine(@NotNull String prompt) {
      return io.readLine(prompt);
    }

    @Override public void println(@NotNull String x) {
      io.out().println(x);
    }

    @Override public void errPrintln(@NotNull String x) {
      io.err().println(x);
    }

    @Override protected @Nullable String hintMessage() {
      return "Note: You are using the plain REPL. Some features may not be available.";
    }
  }
}
