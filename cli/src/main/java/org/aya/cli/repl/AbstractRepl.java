// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import kala.collection.Seq;
import org.aya.api.distill.AyaDocile;
import org.aya.api.distill.DistillerOptions;
import org.aya.cli.repl.command.CommandExecutor;
import org.aya.cli.repl.command.DefaultCommandExecutor;
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
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class AbstractRepl implements Closeable {
  public static final @NotNull @Nls String APP_NAME = "Aya REPL";
  public static final @NotNull @Nls String HELLO = APP_NAME + "\n" +
    "Version: " + GeneratedVersion.VERSION_STRING + "\n" +
    "Commit: " + GeneratedVersion.COMMIT_HASH;
  public String prompt = "> ";

  private static Path CONFIG_ROOT;

  protected static @Nullable Path configRoot() {
    if (CONFIG_ROOT == null) {
      String ayaHome = System.getenv("AYA_HOME");
      CONFIG_ROOT = ayaHome == null ? Paths.get(System.getProperty("user.home"), ".aya") : Paths.get(ayaHome);
    }
    try {
      Files.createDirectories(CONFIG_ROOT);
    } catch (IOException ignored) {
      CONFIG_ROOT = null;
    }
    return CONFIG_ROOT;
  }

  {
    var root = configRoot();
    if (root != null) try {
      prompt = Files.readString(root.resolve("repl_prompt"), StandardCharsets.UTF_8);
    } catch (IOException ignored) {
    }
  }

  private final @NotNull ReplCompiler replCompiler = new ReplCompiler(makeReplReporter(), null);

  private @NotNull CliReporter makeReplReporter() {
    return new CliReporter(this::println, this::errPrintln);
  }

  protected abstract @NotNull String readLine();

  // should flush
  protected abstract void println(@NotNull String x);
  protected abstract void errPrintln(@NotNull String x);

  void run() {
    println(HELLO);
    var additionalMessage = getAdditionalMessage();
    if (additionalMessage != null) println(additionalMessage);
    //noinspection StatementWithEmptyBody
    while (singleLoop()) ;
  }

  protected abstract @Nullable String getAdditionalMessage();

  protected CommandExecutor commandExecutor = new DefaultCommandExecutor();

  /**
   * Executes a single REPL loop.
   *
   * @return <code>true</code> if the REPL should continue to receive user input and execute,
   * <code>false</code> if it should quit.
   */
  private boolean singleLoop() {
    var line = readLine();
    if (line.trim().startsWith(":")) {
      var result = commandExecutor.execute(line.substring(line.indexOf(':') + 1), this);
      println(result.text());
      return result.continueRepl();
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

  private @NotNull String render(@NotNull AyaDocile ayaDocile) {
    return ayaDocile.toDoc(DistillerOptions.DEFAULT).debugRender();
  }

  @Override public void close() throws IOException {
    var root = configRoot();
    if (root != null) Files.writeString(root.resolve("repl_prompt"), prompt, StandardCharsets.UTF_8);
  }
}
