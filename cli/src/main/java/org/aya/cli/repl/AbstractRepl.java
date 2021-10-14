// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import kala.collection.ArraySeq;
import kala.collection.Seq;
import org.aya.api.distill.AyaDocile;
import org.aya.api.distill.DistillerOptions;
import org.aya.cli.single.CliReporter;
import org.aya.prelude.GeneratedVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.PrintWriter;
import java.io.StringWriter;

public abstract class AbstractRepl implements Closeable {
  public static final String INTRODUCTION_MESSAGE = "Aya REPL " + GeneratedVersion.VERSION_STRING;

  // TODO: what locator and builder?
  @NotNull ReplCompiler replCompiler = new ReplCompiler(makeReplReporter(), null, null);

  private @NotNull CliReporter makeReplReporter() {
    return new CliReporter(this::println, this::errPrintln);
  }

  abstract String readLine(@NotNull String prompt);

  // should flush
  abstract void println(@NotNull String x);
  abstract void errPrintln(@NotNull String x);

  void run() {
    println(INTRODUCTION_MESSAGE);
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
  boolean singleLoop() {
    var line = readLine("> ");
    if (line.trim().startsWith(":")) {
      var result = executeCommand(line);
      println(result.text);
      return result.continueRepl;
    } else {
      try {
        var result = evalWithContext(line);
        println(result);
      } catch (Exception e) {
        var stackTrace = new StringWriter();
        e.printStackTrace(new PrintWriter(stackTrace));
        errPrintln("Unexpected exception occurred during execution.");
        errPrintln(stackTrace.toString());
      }
      return true;
    }
  }

  @NotNull String evalWithContext(@NotNull String line) {
    var programOrTerm = replCompiler.compileAndAddToContext(line, Seq.empty(), null);
    return programOrTerm != null ? programOrTerm.fold(
      program -> program.joinToString("\n", this::render),
      this::render
    ) : "The input text is neither a program nor an expression.";
  }

  @NotNull String render(@NotNull AyaDocile ayaDocile) {
    return ayaDocile.toDoc(DistillerOptions.DEFAULT).debugRender();
  }

  static record CommandExecutionResult(@NotNull String text, boolean continueRepl) {
  }

  @NotNull CommandExecutionResult executeCommand(@NotNull String line) {
    var tokens = ArraySeq.wrap(line.split(" "))
      .view()
      .filter(s -> !s.isEmpty())
      .toImmutableVector();
    var firstToken = tokens.get(0);
    return switch (firstToken.substring(1)) {
      case "q", "quit", "exit" -> new CommandExecutionResult("Quitting Aya REPL...", false);
      default -> new CommandExecutionResult("Invalid command \"" + firstToken + "\"", true);
    };
  }
}
