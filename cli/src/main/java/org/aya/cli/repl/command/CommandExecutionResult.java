// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;


import org.aya.cli.repl.ExecutionResultText;
import org.jetbrains.annotations.NotNull;

public record CommandExecutionResult(@NotNull ExecutionResultText executionResultText, boolean continueRepl) {
  public static @NotNull CommandExecutionResult successful(@NotNull String text, boolean continueRepl) {
    return new CommandExecutionResult(ExecutionResultText.successful(text), continueRepl);
  }

  public static @NotNull CommandExecutionResult failed(@NotNull String errText, boolean continueRepl) {
    return new CommandExecutionResult(ExecutionResultText.failed(errText), continueRepl);
  }
}
