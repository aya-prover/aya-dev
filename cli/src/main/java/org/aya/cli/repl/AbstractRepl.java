// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import kala.collection.Seq;
import org.aya.api.distill.AyaDocile;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.util.NormalizeMode;
import org.aya.cli.repl.command.CommandManager;
import org.aya.cli.repl.command.DefaultCommandManager;
import org.aya.cli.repl.config.Configs;
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

  public @NotNull String prompt = "> ";
  public final @NotNull ReplCompiler replCompiler = new ReplCompiler(makeReplReporter(), null);
  public @NotNull CommandManager commandManager = new DefaultCommandManager();
  public @NotNull NormalizeMode normalizeMode = NormalizeMode.NF;

  private static Path CONFIG_ROOT;

  private static @NotNull String readConfig(@NotNull Path root, @NotNull String key) throws IOException {
    return Files.readString(root.resolve(key), StandardCharsets.UTF_8);
  }

  private static void writeConfig(@NotNull Path root, @NotNull String key, @NotNull String value) throws IOException {
    Files.writeString(root.resolve(key), value, StandardCharsets.UTF_8);
  }

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
      for (var configSaver : Configs.configSavers())
        configSaver.deserializeAndSet(this, readConfig(root, configSaver.configKey()));
    } catch (IOException ignored) {
    }
  }

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

  private void printResult(ExecutionResultText executionResultText) {
    var text = executionResultText.text();
    var errText = executionResultText.errText();
    if (text != null) println(text);
    if (errText != null) errPrintln(errText);
  }

  /**
   * Executes a single REPL loop.
   *
   * @return <code>true</code> if the REPL should continue to receive user input and execute,
   * <code>false</code> if it should quit.
   */
  private boolean singleLoop() {
    var line = readLine();
    try {
      if (line.trim().startsWith(":")) {
        var result = commandManager.execute(line.substring(line.indexOf(':') + 1), this);
        printResult(result.executionResultText());
        return result.continueRepl();
      } else
        printResult(evalWithContext(line));
    } catch (Exception e) {
      var stackTrace = new StringWriter();
      e.printStackTrace(new PrintWriter(stackTrace));
      errPrintln(stackTrace.toString());
    }
    return true;
  }

  private @NotNull ExecutionResultText evalWithContext(@NotNull String line) {
    var programOrTerm = replCompiler.compileAndAddToContext(line, normalizeMode, Seq.empty(), null);
    return programOrTerm != null ? ExecutionResultText.successful(programOrTerm.fold(
      program -> program.isEmpty() ? null : program.joinToString("\n", this::render),
      this::render
    )) : ExecutionResultText.failed("The input text is neither a program nor an expression.");
  }

  public @NotNull String render(@NotNull AyaDocile ayaDocile) {
    return ayaDocile.toDoc(DistillerOptions.DEFAULT).debugRender();
  }

  @Override public void close() throws IOException {
    var root = configRoot();
    if (root != null)
      for (var configSaver : Configs.configSavers())
        writeConfig(root, configSaver.configKey(), configSaver.getAndSerialize(this));
  }
}
