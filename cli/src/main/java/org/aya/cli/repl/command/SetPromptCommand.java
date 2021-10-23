// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.repl.AbstractRepl;
import org.jetbrains.annotations.NotNull;

public class SetPromptCommand implements Command {
  public static final SetPromptCommand INSTANCE = new SetPromptCommand();

  private SetPromptCommand() {
  }

  @Override public @NotNull ImmutableSeq<String> names() {
    return ImmutableSeq.of("prompt");
  }

  @Override public @NotNull String description() {
    return "Change the REPL prompt text";
  }

  @Override public @NotNull CommandExecutionResult execute(@NotNull String argument, @NotNull AbstractRepl repl) {
    repl.prompt = argument;
    return CommandExecutionResult.successful("Changed prompt to `" + argument + "`", true);
  }
}
