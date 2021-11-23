// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.single;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.Problem;
import org.aya.api.error.Reporter;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * @author ice1000
 */
public record CliReporter(
  @NotNull BooleanSupplier unicode,
  @NotNull Problem.Severity minimum,
  @NotNull Consumer<String> out,
  @NotNull Consumer<String> err
) implements Reporter {
  @Contract(pure = true, value = "_, _ -> new")
  public static @NotNull CliReporter stdio(boolean unicode, @NotNull Problem.Severity minimum) {
    return new CliReporter(() -> unicode, minimum, System.out::println, System.err::println);
  }

  @Override public void report(@NotNull Problem problem) {
    var level = problem.level();
    if (level.ordinal() > minimum.ordinal()
      // If it's `SourcePos.NONE`, it's a compiler output!
      && problem.sourcePos() != SourcePos.NONE) return;
    var errorMsg = problem.computeFullErrorMessage(DistillerOptions.informative(), unicode.getAsBoolean());
    if (level == Problem.Severity.ERROR || level == Problem.Severity.WARN) err.accept(errorMsg);
    else out.accept(errorMsg);
  }
}
