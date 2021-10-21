// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import org.aya.cli.repl.AbstractRepl;
import org.jetbrains.annotations.NotNull;

public class SetPromptCommand implements SingleLongNameCommand, NoShortNameCommand {
  public static final SetPromptCommand INSTANCE = new SetPromptCommand();

  private SetPromptCommand() {
  }

  @Override
  public @NotNull String longName() {
    return "prompt";
  }

  @Override
  public @NotNull String description() {
    return "Change the REPL prompt text";
  }

  @Override
  public @NotNull CommandExecutionResult execute(@NotNull String argument, @NotNull AbstractRepl repl) {
    repl.prompt = argument;
    return CommandExecutionResult.successful("Changed prompt to `" + argument + "`", true);
  }
}
