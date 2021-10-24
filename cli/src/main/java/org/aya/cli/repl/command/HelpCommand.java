// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Tuple;
import org.aya.cli.repl.Repl;
import org.jetbrains.annotations.NotNull;

public record HelpCommand(@NotNull ImmutableSeq<String> names, @NotNull String description) implements Command {
  public static final HelpCommand INSTANCE = new HelpCommand(
    ImmutableSeq.of("help", "h"), "Show command help");

  @Override
  public @NotNull Command.Result execute(@NotNull String argument, @NotNull Repl repl) {
    var commandTuple2s = repl.commandManager.commands.view()
      .map(command -> Tuple.of(
        command.names().map(name -> Command.PREFIX + name).joinToString(", "),
        command.description())
      );

    var maxWidth = commandTuple2s.map(tuple2 -> tuple2._1.length()).max();
    var helpText = commandTuple2s
      .map(commandTuple2 -> String.format("%-" + maxWidth + "s", commandTuple2._1) + "  " + commandTuple2._2)
      .joinToString("\n");
    return Result.ok("REPL commands:\n" + helpText, true);
  }
}
