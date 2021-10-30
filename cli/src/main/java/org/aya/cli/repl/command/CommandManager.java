// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import org.aya.cli.repl.Repl;
import org.aya.cli.repl.jline.completer.CmdCompleter;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.Completer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;

public class CommandManager {
  public static record CommandGen(@NotNull Command owner, @NotNull MethodHandle entry) {
    public Command.Result invoke(@NotNull Repl repl, @NotNull String argument) throws Throwable {
      return (Command.Result) entry.invoke(owner, repl, argument);
    }
  }

  public final @NotNull ImmutableSeq<@NotNull CommandGen> cmd;

  public CommandManager(@NotNull ImmutableSeq<Command> commands) {
    this.cmd = commands.map(this::genCommand);
  }

  private @NotNull CommandGen genCommand(Command c) {
    var entry = Arrays.stream(c.getClass().getDeclaredMethods())
      .filter(method -> method.isAnnotationPresent(Command.Entry.class))
      .findFirst();
    if (entry.isEmpty()) throw new IllegalArgumentException("no entry found in " + c.getClass());
    try {
      return new CommandGen(c, MethodHandles.lookup().unreflect(entry.get()));
    } catch (IllegalAccessException e) {
      throw new IllegalArgumentException("unable to unreflect: ", e);
    }
  }

  public record Clue(
    @NotNull String name,
    @NotNull Option<@NotNull CommandGen> command,
    @NotNull String argument
  ) {
    public Command.Result run(@NotNull Repl repl) throws Throwable {
      return command.isDefined()
        ? command.get().invoke(repl, argument)
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
    return new Clue(name, cmd.findFirst(c -> c.owner.names().contains(name)), argument);
  }

  public @NotNull Completer completer() {
    return new CmdCompleter(cmd.view().flatMap(c -> c.owner.names()));
  }
}
