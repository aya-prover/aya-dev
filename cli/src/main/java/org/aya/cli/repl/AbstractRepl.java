// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli.repl;

import kala.collection.ArraySeq;
import kala.collection.Seq;
import org.aya.api.distill.AyaDocile;
import org.aya.api.distill.DistillerOptions;
import org.aya.prelude.GeneratedVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;

public abstract class AbstractRepl implements Closeable {
  public static final String INTRODUCTION_MESSAGE = "Aya REPL " + GeneratedVersion.VERSION_STRING;

  // TODO: what locator and builder?
  ReplCompiler replCompiler = new ReplCompiler(new ReplReporter(this), null, null);

  abstract String readLine(String prompt);

  // should flush
  abstract void println(String x);
  abstract void errPrintln(String x);

  void run() {
    println(INTRODUCTION_MESSAGE);
    var additionalMessage = getAdditionalMessage();
    if (additionalMessage != null)
      println(additionalMessage);
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
        errPrintln("Unexpected exception occurred during execution.");
        e.printStackTrace();
      }
      return true;
    }
  }

  @NotNull String evalWithContext(String line) {
    var programOrTerm = replCompiler.compileAndAddToContext(line, Seq.empty(), null);
    return programOrTerm != null ? programOrTerm.fold(
      program -> program.joinToString("\n", this::render),
      this::render
    ) : "The input text is neither a program nor an expression.";
  }

  @NotNull String render(@NotNull AyaDocile ayaDocile) {
    return ayaDocile.toDoc(DistillerOptions.DEFAULT).debugRender();
  }

  static record CommandExecutionResult(String text, boolean continueRepl) {
  }

  CommandExecutionResult executeCommand(String line) {
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
