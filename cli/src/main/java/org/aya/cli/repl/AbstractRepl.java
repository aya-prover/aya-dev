// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import kala.collection.Seq;
import org.aya.api.distill.AyaDocile;
import org.aya.api.distill.DistillerOptions;
import org.aya.cli.single.CliReporter;
import org.aya.prelude.GeneratedVersion;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public abstract class AbstractRepl implements Closeable {
  public static final @NotNull @Nls String APP_NAME = "Aya REPL";
  public static final @NotNull @Nls String HELLO = APP_NAME + "\n" +
    "Version: " + GeneratedVersion.VERSION_STRING + "\n" +
    "Commit: " + GeneratedVersion.COMMIT_HASH;
  protected String prompt = "> ";

  {
    var root = Repl.configRoot();
    if (root != null) try {
      prompt = Files.readString(root.resolve("repl_prompt"), StandardCharsets.UTF_8);
    } catch (IOException ignored) {
    }
  }

  private final @NotNull ReplCompiler replCompiler = new ReplCompiler(makeReplReporter(), null);

  private @NotNull CliReporter makeReplReporter() {
    return new CliReporter(this::println, this::errPrintln);
  }

  abstract String readLine();

  // should flush
  abstract void println(@NotNull String x);
  abstract void errPrintln(@NotNull String x);

  void run() {
    println(HELLO);
    var additionalMessage = getAdditionalMessage();
    if (additionalMessage != null) println(additionalMessage);
    //noinspection StatementWithEmptyBody
    while (singleLoop()) ;
  }

  @Nullable abstract String getAdditionalMessage();

  /**
   * Executes a single REPL loop.
   *
   * @return <code>true</code> if the REPL should continue to receive user input and execute,
   * <code>false</code> if it should quit.
   */
  private boolean singleLoop() {
    var line = readLine();
    if (line.trim().startsWith(":")) {
      var result = executeCommand(line);
      println(result.text);
      return result.continueRepl;
    } else {
      try {
        println(evalWithContext(line));
      } catch (Exception e) {
        var stackTrace = new StringWriter();
        e.printStackTrace(new PrintWriter(stackTrace));
        errPrintln(stackTrace.toString());
      }
      return true;
    }
  }

  private @NotNull String evalWithContext(@NotNull String line) {
    var programOrTerm = replCompiler.compileAndAddToContext(line, Seq.empty(), null);
    return programOrTerm != null ? programOrTerm.fold(
      program -> program.joinToString("\n", this::render),
      this::render
    ) : "The input text is neither a program nor an expression.";
  }

  @NotNull String render(@NotNull AyaDocile ayaDocile) {
    return ayaDocile.toDoc(DistillerOptions.DEFAULT).debugRender();
  }

  record CommandExecutionResult(@NotNull String text, boolean continueRepl) {
  }

  @NotNull CommandExecutionResult executeCommand(@NotNull String line) {
    var split = line.split(" ", 2);
    var command = split[0];
    var argument = split.length > 1 ? split[1] : "";
    return switch (command.substring(1)) {
      case "q", "quit", "exit" -> new CommandExecutionResult("Quitting Aya REPL...", false);
      case "prompt" -> {
        prompt = argument;
        yield new CommandExecutionResult("Changed prompt to `" + argument + "`", true);
      }
      default -> new CommandExecutionResult("Invalid command \"" + command + "\"", true);
    };
  }

  @Override public void close() throws IOException {
    var root = Repl.configRoot();
    if (root != null) Files.writeString(root.resolve("repl_prompt"), prompt, StandardCharsets.UTF_8);
  }
}
