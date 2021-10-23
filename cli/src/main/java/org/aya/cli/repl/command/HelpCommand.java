// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Tuple2;
import org.aya.cli.repl.AbstractRepl;
import org.jetbrains.annotations.NotNull;

public class HelpCommand implements Command {
  private HelpCommand() {
  }

  public static final HelpCommand INSTANCE = new HelpCommand();

  @Override public @NotNull ImmutableSeq<String> names() {
    return ImmutableSeq.of("help", "h");
  }

  @Override public @NotNull String description() {
    return "Show command help";
  }

  @Override
  public @NotNull CommandExecutionResult execute(@NotNull String argument, @NotNull AbstractRepl repl) {
    var commandTuple2s = repl.commandManager.commands
      .map(command -> new Tuple2<>(
        command.names().joinToString(", ", name -> ':' + name), command.description()));

    var maxWidth = commandTuple2s.view().map(tuple2 -> tuple2._1.length()).max();

    var helpText = "REPL commands\n" + commandTuple2s.joinToString("\n",
      commandTuple2 -> String.format("%-" + maxWidth + "s", commandTuple2._1) + "  " + commandTuple2._2);

    return CommandExecutionResult.successful(helpText, true);
  }
}
