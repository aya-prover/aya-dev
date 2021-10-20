// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.repl.AbstractRepl;
import org.jetbrains.annotations.NotNull;

public final class QuitCommand implements SingleShortNameCommand {
  public static final QuitCommand INSTANCE = new QuitCommand();

  private QuitCommand() {
  }

  @Override
  public @NotNull ImmutableSeq<String> longNames() {
    return ImmutableSeq.of("quit", "exit");
  }

  @Override
  public char shortName() {
    return 'q';
  }

  @Override
  public @NotNull String description() {
    return "Quit the REPL";
  }

  @Override
  public CommandExecutionResult execute(@NotNull String argument, @NotNull AbstractRepl repl) {
    return new CommandExecutionResult("See you space cow woof woof :3", false);
  }
}
