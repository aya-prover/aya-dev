// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import org.aya.cli.console.AnsiReporter;
import org.aya.cli.console.MainArgs;
import org.aya.cli.interactive.ReplCompiler;
import org.aya.cli.interactive.ReplConfig;
import org.aya.cli.render.RenderOptions;
import org.aya.cli.repl.jline.AyaCompleters;
import org.aya.cli.repl.jline.JlineRepl;
import org.aya.generic.AyaDocile;
import org.aya.prelude.GeneratedVersion;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.pretty.doc.Doc;
import org.aya.repl.*;
import org.aya.syntax.literate.CodeOptions;
import org.aya.util.reporter.Problem;
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
    var pathArg = CommandArg.shellLike(Path.class, new Completers.FileNameCompleter(), this::resolveFile);
    return new CommandManager(AyaRepl.class, ImmutableSeq.of(
      CommandArg.STRING,
      CommandArg.STRICT_BOOLEAN,
      CommandArg.STRICT_INT,
      pathArg,
      CommandArg.from(ReplCommands.Code.class, new AyaCompleters.Code(this), ReplCommands.Code::new),
      CommandArg.from(ReplUtil.HelpItem.class, new ReplCompleters.Help(() -> commandManager), ReplUtil.HelpItem::new),
      CommandArg.from(ReplCommands.Prompt.class, null, ReplCommands.Prompt::new),
      CommandArg.fromEnum(AyaPrettierOptions.Key.class),
      CommandArg.fromEnum(CodeOptions.NormalizeMode.class),
      CommandArg.fromEither(ReplCommands.ColorParam.class,
        CommandArg.fromEnum(RenderOptions.ColorSchemeName.class),
        pathArg,
        l -> new ReplCommands.ColorParam(Either.left(l)),
        r -> new ReplCommands.ColorParam(Either.right(r))),
      CommandArg.fromEither(ReplCommands.StyleParam.class,
        CommandArg.fromEnum(RenderOptions.StyleFamilyName.class),
        pathArg,
        l -> new ReplCommands.StyleParam(Either.left(l)),
        r -> new ReplCommands.StyleParam(Either.right(r)))
    ), ImmutableSeq.of(
      ReplCommands.HELP,
      ReplCommands.QUIT,
      ReplCommands.CHANGE_PROMPT,
      ReplCommands.CHANGE_NORM_MODE,
      ReplCommands.TOGGLE_PRETTY,
      ReplCommands.SHOW_TYPE,
      ReplCommands.SHOW_PARSE_TREE,
      ReplCommands.CHANGE_PP_WIDTH,
      ReplCommands.TOGGLE_UNICODE,
      ReplCommands.CHANGE_CWD,
      ReplCommands.PRINT_CWD,
      ReplCommands.LOAD,
      ReplCommands.COLOR,
      ReplCommands.STYLE
    ));
  }

  public final @NotNull ReplConfig config;
  public @NotNull Path cwd = Path.of(System.getProperty("user.dir"));
  public int prettyPrintWidth = 80;
  public final @NotNull ReplCompiler replCompiler;
  public final @NotNull CommandManager commandManager = makeCommand();

  public AyaRepl(@NotNull ImmutableSeq<Path> modulePaths, @NotNull ReplConfig config) {
    this.config = config;
    replCompiler = new ReplCompiler(modulePaths, new AnsiReporter(true,
      () -> config.enableUnicode, () -> config.literatePrettier.prettierOptions,
      Problem.Severity.INFO, this::println, this::errPrintln), null);
  }

  protected abstract @Nullable String hintMessage();

  public @NotNull Path resolveFile(@NotNull String arg) {
    return ReplUtil.resolveFile(arg, cwd);
  }

  @Override public void run() {
    if (!config.silent) {
      println("Aya " + GeneratedVersion.VERSION_STRING + " (" + GeneratedVersion.COMMIT_HASH + ")");
      var hint = hintMessage();
      if (hint != null) println(hint);
    }
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
      program -> config.silent ? Doc.empty() :
        Doc.vcat(program.view().map(def -> def.toDoc(config.literatePrettier.prettierOptions))),
      this::render
    ));
  }

  public @NotNull Doc render(@NotNull AyaDocile ayaDocile) {
    return ayaDocile.toDoc(config.literatePrettier.prettierOptions);
  }

  @Override public @NotNull String renderDoc(@NotNull Doc doc) {
    return doc.renderToString(prettyPrintWidth, config.enableUnicode);
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
