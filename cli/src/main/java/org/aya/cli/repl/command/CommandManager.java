// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.control.Try;
import kala.tuple.Tuple;
import org.aya.cli.repl.Repl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;

public class CommandManager {
  public record CommandGen(
    @NotNull Command owner,
    @NotNull MethodHandle entry,
    @Nullable CommandArg argFactory
  ) {
    public Command.Result invoke(@NotNull Repl repl, @NotNull String argument) throws Throwable {
      if (argFactory != null) return (Command.Result) entry.invoke(owner, repl,
        Try.of(() -> argFactory.parse(argument)).getOrNull());
      else return (Command.Result) entry.invoke(owner, repl);
    }
  }

  public final @NotNull ImmutableSeq<@NotNull CommandGen> cmd;
  public final @NotNull ImmutableMap<Class<?>, CommandArg> argFactory;

  public CommandManager(@NotNull ImmutableSeq<CommandArg> argFactory, @NotNull ImmutableSeq<Command> commands) {
    this.argFactory = argFactory.view().map(c -> Tuple.of(c.type(), c)).toImmutableMap();
    this.cmd = commands.map(this::genCommand);
  }

  private @NotNull CommandGen genCommand(@NotNull Command c) {
    var entry = Arrays.stream(c.getClass().getDeclaredMethods())
      .filter(method -> method.isAnnotationPresent(Command.Entry.class))
      .findFirst();
    if (entry.isEmpty()) throw new IllegalArgumentException("no entry found in " + c.getClass());
    try {
      var method = entry.get();
      method.setAccessible(true); // for anonymous class
      var handle = MethodHandles.lookup().unreflect(method);
      var param = handle.type().parameterList();
      if (param.size() < 2)
        throw new IllegalArgumentException("entry method must at least have 1 parameters (the `REPL` instance)");
      if (param.get(1) != Repl.class)
        throw new IllegalArgumentException("entry method must have `Repl` as first parameter");
      if (param.size() == 2) return new CommandGen(c, handle, null);
      // TODO: support more than 1 parameter
      var factory = argFactory.getOption(param.get(2));
      if (factory.isEmpty()) throw new IllegalArgumentException("no argument factory found for " + param.get(2));
      return new CommandGen(c, handle, factory.get());
    } catch (IllegalAccessException e) {
      throw new IllegalArgumentException("unable to unreflect entry for: " + c.names(), e);
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
}
