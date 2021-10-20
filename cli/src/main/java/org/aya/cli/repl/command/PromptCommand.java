// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.repl.AbstractRepl;
import org.jetbrains.annotations.NotNull;

public class PromptCommand implements NoShortNameCommand {
  public static final PromptCommand INSTANCE = new PromptCommand();

  private PromptCommand() {
  }

  @Override
  public @NotNull ImmutableSeq<String> longNames() {
    return ImmutableSeq.of("prompt");
  }

  @Override
  public @NotNull String description() {
    return "Change the REPL prompt text";
  }

  @Override
  public CommandExecutionResult execute(@NotNull String argument, @NotNull AbstractRepl repl) {
    repl.prompt = argument;
    return new CommandExecutionResult("Changed prompt to `" + argument + "`", true);
  }
}
