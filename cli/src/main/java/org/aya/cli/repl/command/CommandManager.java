// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import org.aya.cli.repl.AbstractRepl;
import org.aya.cli.repl.ExecutionResultText;
import org.aya.cli.repl.jline.completer.CommandCompleter;
import org.jetbrains.annotations.NotNull;

public class CommandManager {
  public final @NotNull ImmutableSeq<Command> commands;
  public final @NotNull ImmutableMap<@NotNull String, @NotNull Command> commandMap;

  public CommandManager(@NotNull ImmutableSeq<Command> commands) throws CommandException {
    this.commands = commands;

    var commandMap = new MutableHashMap<@NotNull String, @NotNull Command>();
    for (var command : commands) {
      if (!command.hasAtLeastOneName())
        throw new CommandException("Command " + command + " has no names");

      for (var name : command.names()) {
        var existingCommand = commandMap.putIfAbsent(name, command);
        if (existingCommand.isDefined())
          throw new CommandException("Command " + existingCommand.get() +
            " and command " + command + " has a duplicate name " + name);
      }
    }

    this.commandMap = commandMap.toImmutableMap();
  }

  /**
   * @param text the command text without ":"
   * @param repl the REPL
   * @return the result
   */
  public @NotNull CommandExecutionResult execute(@NotNull String text, @NotNull AbstractRepl repl) {
    var split = text.split(" ", 2);
    var name = split[0];
    var argument = split.length > 1 ? split[1] : "";

    var command = commandMap.getOption(name);
    return command.isDefined() ?
      command.get().execute(argument, repl) :
      new CommandExecutionResult(ExecutionResultText.failed("Invalid command \"" + name + "\""), true);
  }

  public @NotNull CommandCompleter completer() {
    return new CommandCompleter(commandMap.keysView());
  }
}
