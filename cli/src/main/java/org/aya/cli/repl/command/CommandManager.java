// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.control.Option;
import org.aya.cli.repl.Repl;
import org.aya.cli.repl.jline.completer.CommandCompleter;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.Completer;

public class CommandManager {
  public final @NotNull ImmutableSeq<Command> commands;
  public final @NotNull ImmutableMap<@NotNull String, @NotNull Command> commandMap;

  public CommandManager(@NotNull ImmutableSeq<Command> commands) {
    this.commands = commands;

    var commandMap = new MutableHashMap<@NotNull String, @NotNull Command>();
    for (var command : commands) {
      assert command.names().isNotEmpty() : "Command " + command + " has no names";

      for (var name : command.names()) {
        var existingCommand = commandMap.putIfAbsent(name, command);
        if (existingCommand.isDefined())
          throw new IllegalArgumentException("Command " + existingCommand.get() +
            " and command " + command + " has a duplicate name " + name);
      }
    }

    this.commandMap = commandMap.toImmutableMap();
  }

  public record Clue(
    @NotNull String name,
    @NotNull Option<@NotNull Command> command,
    @NotNull String argument
  ) {
    public Command.Result run(@NotNull Repl repl) {
      return command.isDefined()
        ? command.get().execute(argument, repl)
        : Command.Result.err("Command `" + name + "` not found", true);
    }
  }

  /**
   * @param text the command text without ":"
   * @return the execution plan
   */
  public @NotNull CommandManager.Clue parse(@NotNull String text) {
    var split = text.split(" +", 2);
    var name = split[0];
    var argument = split.length > 1 ? split[1] : "";
    return new Clue(name, commandMap.getOption(name), argument);
  }

  public @NotNull Completer completer() {
    return new CommandCompleter(commandMap.keysView());
  }
}
