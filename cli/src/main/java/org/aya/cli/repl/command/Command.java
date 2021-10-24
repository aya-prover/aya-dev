// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import org.aya.cli.repl.Repl;
import org.jetbrains.annotations.NotNull;

public interface Command {
  @NotNull String PREFIX = ":";

  @NotNull ImmutableSeq<String> names();
  @NotNull String description();

  /**
   * Execute the command.
   *
   * @param argument the command content such as args and code with the command prefix removed
   * @param repl     the REPL
   * @return the result
   */
  @NotNull Command.Result execute(@NotNull String argument, @NotNull Repl repl);

  record Result(@NotNull Either<String, String> resultText, boolean continueRepl) {
    public static @NotNull Command.Result ok(@NotNull String text, boolean continueRepl) {
      return new Result(Either.right(text), continueRepl);
    }

    public static @NotNull Command.Result err(@NotNull String errText, boolean continueRepl) {
      return new Result(Either.left(errText), continueRepl);
    }
  }
}
