// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import kala.collection.Seq;
import org.aya.api.distill.AyaDocile;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.util.AyaHome;
import org.aya.cli.repl.command.Command;
import org.aya.cli.repl.command.CommandManager;
import org.aya.cli.repl.command.DefaultCommands;
import org.aya.cli.repl.jline.JlineRepl;
import org.aya.cli.single.CliReporter;
import org.aya.cli.utils.MainArgs;
import org.aya.prelude.GeneratedVersion;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Scanner;

public abstract class Repl implements Closeable {
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
  public final @NotNull CommandManager commandManager = DefaultCommands.defaultCommandManager();
  public final @NotNull ReplConfig replConfig;
  public int prettyPrintWidth = 80;

  public Repl(@NotNull ReplConfig config) {
    this.replConfig = config;
  }

  protected abstract void println(@NotNull String x);
  protected abstract void errPrintln(@NotNull String x);
  protected abstract @NotNull String readLine(@NotNull String prompt) throws EOFException, InterruptedException;
  protected abstract @Nullable String hintMessage();

  void run() {
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
    try {
      var line = readLine(replConfig.prompt).trim();
      if (line.startsWith(Command.PREFIX)) {
        var result = commandManager.execute(line.substring(1), this);
        printResult(result.output());
        return result.continueRepl();
      } else printResult(evalWithContext(line));
    } catch (EOFException ignored) {
      // user send ctrl-d
      return false;
    } catch (InterruptedException ignored) {
      // user send ctrl-c
      return true;
    } catch (Exception e) {
      var stackTrace = new StringWriter();
      e.printStackTrace(new PrintWriter(stackTrace));
      errPrintln(stackTrace.toString());
    }
    return true;
  }

  private @NotNull Command.Output evalWithContext(@NotNull String line) {
    var programOrTerm = replCompiler.compileAndAddToContext(line, replConfig.normalizeMode, Seq.empty(), null);
    return programOrTerm != null ? Command.Output.stdout(programOrTerm.fold(
      program -> render(options -> Doc.vcat(program.view().map(def -> def.toDoc(options)))),
      this::render
    )) : Command.Output.stderr("The input text is neither a program nor an expression.");
  }

  public @NotNull String render(@NotNull AyaDocile ayaDocile) {
    return renderDoc(ayaDocile.toDoc(DistillerOptions.DEFAULT));
  }

  public @NotNull String renderDoc(@NotNull Doc doc) {
    return doc.renderWithPageWidth(prettyPrintWidth, replConfig.enableUnicode);
  }

  @Override public void close() throws IOException {
    replConfig.close();
  }

  /**
   * Default repl when jline is unavailable
   */
  public static class PlainRepl extends Repl {
    private final @NotNull Scanner scanner = new Scanner(System.in);

    public PlainRepl(@NotNull ReplConfig config) {
      super(config);
    }

    @Override protected @NotNull String readLine(@NotNull String prompt) {
      System.out.print(prompt);
      System.out.flush();
      return scanner.nextLine();
    }

    @Override protected void println(@NotNull String x) {
      System.out.println(x);
    }

    @Override protected void errPrintln(@NotNull String x) {
      System.err.println(x);
    }

    @Override protected @Nullable String hintMessage() {
      return "Note: You are using the plain REPL. Some features may not be available.";
    }
  }
}
