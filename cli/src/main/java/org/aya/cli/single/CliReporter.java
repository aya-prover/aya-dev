// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.single;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.Problem;
import org.aya.api.error.Reporter;
import org.aya.pretty.backend.string.StringPrinterConfig;
import org.aya.pretty.printer.PrinterConfig;
import org.aya.pretty.style.AyaStyleFamily;
import org.aya.util.error.SourcePos;
import org.fusesource.jansi.AnsiConsole;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author ice1000
 */
public record CliReporter(
  boolean supportAnsi,
  @NotNull BooleanSupplier unicode,
  @NotNull Supplier<DistillerOptions> options,
  @NotNull Problem.Severity minimum,
  @NotNull Consumer<String> out,
  @NotNull Consumer<String> err
) implements Reporter {
  @Contract(pure = true, value = "_, _, _ -> new")
  public static @NotNull CliReporter stdio(boolean unicode, @NotNull DistillerOptions options, @NotNull Problem.Severity minimum) {
    AnsiConsole.systemInstall();
    return new CliReporter(true, () -> unicode, () -> options, minimum,
      AnsiConsole.out()::println, AnsiConsole.err()::println);
  }

  @Override public void report(@NotNull Problem problem) {
    var level = problem.level();
    if (level.ordinal() > minimum.ordinal()
      // If it's `SourcePos.NONE`, it's a compiler output!
      && problem.sourcePos() != SourcePos.NONE) return;
    var errorMsg = errorMessage(problem, options.get(), unicode.getAsBoolean(), supportAnsi, terminalWidth());
    if (level == Problem.Severity.ERROR || level == Problem.Severity.WARN) err.accept(errorMsg);
    else out.accept(errorMsg);
  }

  private int terminalWidth() {
    int w = AnsiConsole.getTerminalWidth();
    // output is redirected to a file, so it has infinite width
    return w <= 0 ? PrinterConfig.INFINITE_SIZE : w;
  }

  public static @NotNull String errorMessage(
    @NotNull Problem problem, @NotNull DistillerOptions options,
    boolean unicode, boolean supportAnsi, int pageWidth
  ) {
    var doc = problem.sourcePos() == SourcePos.NONE ? problem.describe(options) : problem.toPrettyError(options).toDoc();
    if (supportAnsi) {
      var config = StringPrinterConfig.unixTerminal(pageWidth, unicode);
      config.getStylist().setStyleFamily(AyaStyleFamily.ADAPTIVE_CLI);
      return doc.renderToString(config);
    }
    return doc.renderWithPageWidth(pageWidth, unicode);
  }
}
