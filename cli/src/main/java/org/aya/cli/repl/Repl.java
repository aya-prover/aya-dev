// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.distill.AyaDocile;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.util.AyaHome;
import org.aya.api.util.InterruptException;
import org.aya.api.util.NormalizeMode;
import org.aya.cli.repl.command.Command;
import org.aya.cli.repl.command.CommandArg;
import org.aya.cli.repl.command.CommandManager;
import org.aya.cli.repl.jline.JlineRepl;
import org.aya.cli.repl.jline.completer.AyaCompleters;
import org.aya.cli.single.CliReporter;
import org.aya.cli.utils.MainArgs;
import org.aya.prelude.GeneratedVersion;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jline.builtins.Completers;

import java.io.*;
import java.nio.file.Path;
import java.util.Scanner;

public abstract class Repl implements Closeable, Runnable {
  public static int start(MainArgs.@NotNull ReplAction replAction) throws IOException {
    var configFile = AyaHome.ayaHome().resolve("repl_config.json");
    var replConfig = ReplConfig.loadFrom(configFile);
    try (var repl = makeRepl(replAction, replConfig)) {
      repl.run();
    }
    return 0;
  }

  @NotNull
  private static Repl makeRepl(MainArgs.@NotNull ReplAction replAction, ReplConfig replConfig) throws IOException {
    return switch (replAction.replType) {
      case jline -> new JlineRepl(replConfig);
      case plain -> new PlainRepl(replConfig);
    };
  }

  public final @NotNull ReplCompiler replCompiler = new ReplCompiler(new CliReporter(this::println, this::errPrintln), null);
  public final @NotNull CommandManager commandManager = makeCommand();

  public CommandManager makeCommand() {
    return new CommandManager(ImmutableSeq.of(
      CommandArg.STRING,
      CommandArg.STRICT_BOOLEAN,
      CommandArg.STRICT_INT,
      CommandArg.shellLike(Path.class, new Completers.FileNameCompleter(), this::resolveFile),
      CommandArg.from(ReplCommands.Code.class, new AyaCompleters.Code(this), ReplCommands.Code::new),
      CommandArg.from(ReplCommands.HelpItem.class, new AyaCompleters.Help(this), ReplCommands.HelpItem::new),
      CommandArg.fromEnum(NormalizeMode.class)
    ), ImmutableSeq.of(
      ReplCommands.HELP,
      ReplCommands.QUIT,
      ReplCommands.CHANGE_PROMPT,
      ReplCommands.CHANGE_NORM_MODE,
      ReplCommands.SHOW_TYPE,
      ReplCommands.CHANGE_PP_WIDTH,
      ReplCommands.TOGGLE_UNICODE,
      ReplCommands.CHANGE_CWD,
      ReplCommands.PRINT_CWD,
      ReplCommands.LOAD_FILE
    ));
  }

  public final @NotNull ReplConfig config;
  public @NotNull Path cwd = Path.of("");
  public int prettyPrintWidth = 80;

  public Repl(@NotNull ReplConfig config) {
    this.config = config;
  }

  protected abstract void println(@NotNull String x);
  protected abstract void errPrintln(@NotNull String x);
  protected abstract @NotNull String readLine(@NotNull String prompt) throws EOFException, InterruptedException;
  protected abstract @Nullable String hintMessage();

  public @NotNull Path resolveFile(@NotNull String arg) {
    var homeAware = arg.replaceFirst("^~", System.getProperty("user.home"));
    var path = Path.of(homeAware);
    return path.isAbsolute() ? path.normalize() : cwd.resolve(homeAware).toAbsolutePath().normalize();
  }

  @Override public void run() {
    println("Aya " + GeneratedVersion.VERSION_STRING + " (" + GeneratedVersion.COMMIT_HASH + ")");
    var hint = hintMessage();
    if (hint != null) println(hint);
    //noinspection StatementWithEmptyBody
    while (singleLoop()) ;
  }

  private void printResult(@NotNull Command.Output output) {
    if (output.stdout().isNotEmpty()) println(renderDoc(output.stdout()));
    if (output.stderr().isNotEmpty()) errPrintln(renderDoc(output.stderr()));
  }

  /**
   * Executes a single REPL loop.
   *
   * @return <code>true</code> if the REPL should continue to receive user input and execute,
   * <code>false</code> if it should quit.
   */
  private boolean singleLoop() {
    replCompiler.reporter.clear();
    try {
      var line = readLine(config.prompt).trim();
      if (line.startsWith(Command.MULTILINE_BEGIN) && line.endsWith(Command.MULTILINE_END)) {
        var code = line.substring(Command.MULTILINE_BEGIN.length(), line.length() - Command.MULTILINE_END.length());
        printResult(eval(code));
      } else if (line.startsWith(Command.PREFIX)) {
        var result = commandManager.parse(line.substring(1)).run(this);
        printResult(result.output());
        return result.continueRepl();
      } else printResult(eval(line));
    } catch (EOFException ignored) {
      // user send ctrl-d
      return false;
    } catch (InterruptedException ignored) {
      // user send ctrl-c
    } catch (InterruptException ignored) {
      // compilation errors are already printed by reporters
    } catch (Throwable e) {
      var stackTrace = new StringWriter();
      e.printStackTrace(new PrintWriter(stackTrace));
      errPrintln(stackTrace.toString());
    }
    return true;
  }

  private @NotNull Command.Output eval(@NotNull String line) {
    var programOrTerm = replCompiler.compileToContext(line, config.normalizeMode);
    return Command.Output.stdout(programOrTerm.fold(
      program -> render(options -> Doc.vcat(program.view().map(def -> def.toDoc(options)))),
      this::render
    ));
  }

  public @NotNull Doc render(@NotNull AyaDocile ayaDocile) {
    return ayaDocile.toDoc(DistillerOptions.informative());
  }

  public @NotNull String renderDoc(@NotNull Doc doc) {
    return doc.renderWithPageWidth(prettyPrintWidth, config.enableUnicode);
  }

  @Override public void close() throws IOException {
    config.close();
  }

  /**
   * Default repl when jline is unavailable
   */
  public static class PlainRepl extends Repl {
    private final @NotNull Scanner scanner;
    private final @NotNull PrintWriter out;
    private final @NotNull PrintWriter err;

    public PlainRepl(@NotNull ReplConfig config) {
      this(config, new InputStreamReader(System.in), new PrintWriter(System.out), new PrintWriter(System.err));
    }

    public PlainRepl(
      @NotNull ReplConfig config,
      @NotNull Readable input,
      @NotNull Writer out,
      @NotNull Writer err
    ) {
      super(config);
      scanner = new Scanner(input);
      this.out = new PrintWriter(out);
      this.err = new PrintWriter(err);
    }

    @Override protected @NotNull String readLine(@NotNull String prompt) {
      out.print(prompt);
      out.flush();
      return scanner.nextLine();
    }

    @Override protected void println(@NotNull String x) {
      out.println(x);
    }

    @Override protected void errPrintln(@NotNull String x) {
      err.println(x);
    }

    @Override protected @Nullable String hintMessage() {
      return "Note: You are using the plain REPL. Some features may not be available.";
    }
  }
}
