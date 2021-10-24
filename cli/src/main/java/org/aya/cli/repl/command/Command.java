// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.repl.AbstractRepl;
import org.aya.cli.repl.ExecutionResultText;
import org.jetbrains.annotations.NotNull;

public interface Command {
  @NotNull ImmutableSeq<String> names();
  @NotNull String description();

  /**
   * Execute the command.
   *
   * @param argument the command content such as args and code with the command prefix removed
   * @param repl     the REPL
   * @return the result
   */
  @NotNull Command.Result execute(@NotNull String argument, @NotNull AbstractRepl repl);

  record Result(@NotNull ExecutionResultText executionResultText, boolean continueRepl) {
    public static @NotNull Command.Result successful(@NotNull String text, boolean continueRepl) {
      return new Result(ExecutionResultText.successful(text), continueRepl);
    }

    public static @NotNull Command.Result failed(@NotNull String errText, boolean continueRepl) {
      return new Result(ExecutionResultText.failed(errText), continueRepl);
    }
  }
}
