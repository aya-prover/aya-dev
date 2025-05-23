// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.console;

import org.aya.pretty.printer.PrinterConfig;
import org.aya.repl.ReplUtil;
import org.aya.util.PrettierOptions;
import org.aya.util.position.SourcePos;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
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
    if (unicode) try {
      var out = ReplUtil.jlineDumbTerminalWriter();
      return new AnsiReporter(true, () -> true, () -> options, minimum, out, out);
    } catch (Exception _) {
    }
    return new AnsiReporter(true, () -> unicode, () -> options, minimum,
      System.out::println, System.err::println);
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
    // int w = AnsiConsole.getTerminalWidth();
    // // output is redirected to a file, so it has infinite width
    // return w <= 0 ? PrinterConfig.INFINITE_SIZE : w;
    return PrinterConfig.INFINITE_SIZE;
  }
}
