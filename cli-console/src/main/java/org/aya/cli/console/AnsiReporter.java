// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.console;

import org.aya.pretty.printer.PrinterConfig;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
import org.fusesource.jansi.AnsiConsole;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author ice1000
 */
public record AnsiReporter(
  boolean supportAnsi,
  @NotNull BooleanSupplier unicode,
  @NotNull Supplier<PrettierOptions> options,
  @NotNull Problem.Severity minimum,
  @NotNull Consumer<String> out,
  @NotNull Consumer<String> err
) implements Reporter {
  @Contract(pure = true, value = "_, _, _ -> new")
  public static @NotNull AnsiReporter stdio(boolean unicode, @NotNull PrettierOptions options, @NotNull Problem.Severity minimum) {
    AnsiConsole.systemInstall();
    return new AnsiReporter(true, () -> unicode, () -> options, minimum,
      AnsiConsole.out()::println, AnsiConsole.err()::println);
  }

  @Override public void report(@NotNull Problem problem) {
    var level = problem.level();
    if (level.ordinal() > minimum.ordinal()
      // If it's `SourcePos.NONE`, it's a compiler output!
      && problem.sourcePos() != SourcePos.NONE) return;
    var errorMsg = Reporter.errorMessage(problem, options.get(), unicode.getAsBoolean(), supportAnsi, terminalWidth());
    if (level == Problem.Severity.ERROR || level == Problem.Severity.WARN) err.accept(errorMsg);
    else out.accept(errorMsg);
  }

  private int terminalWidth() {
    int w = AnsiConsole.getTerminalWidth();
    // output is redirected to a file, so it has infinite width
    return w <= 0 ? PrinterConfig.INFINITE_SIZE : w;
  }
}
