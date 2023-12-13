// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.repl;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Try;
import kala.tuple.Tuple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

public class CommandManager {
  public record CommandGen(
    @NotNull Command owner,
    @NotNull MethodHandle entry,
    @Nullable CommandArg argFactory
  ) {
    public Command.Result invoke(@NotNull Object repl, @NotNull String argument) throws Throwable {
      if (argFactory != null) return (Command.Result) entry.invoke(owner, repl,
        Try.of(() -> argFactory.parse(argument)).getOrNull());
      else return (Command.Result) entry.invoke(owner, repl);
    }
  }

  public final @NotNull ImmutableSeq<@NotNull CommandGen> cmd;
  public final @NotNull ImmutableMap<Class<?>, CommandArg> argFactory;
  private final @NotNull Class<?> replClass;

  public CommandManager(
    @NotNull Class<?> replClass,
    @NotNull ImmutableSeq<CommandArg> argFactory,
    @NotNull ImmutableSeq<Command> commands
  ) {
    this.replClass = replClass;
    this.argFactory = ImmutableMap.from(argFactory.view().map(c -> Tuple.of(c.type(), c)));
    this.cmd = commands.map(this::genCommand);
  }

  private @NotNull CommandGen genCommand(@NotNull Command c) {
    var entry = Seq.of(c.getClass().getDeclaredMethods())
      .findFirst(method -> method.isAnnotationPresent(Command.Entry.class));
    if (entry.isEmpty()) throw new IllegalArgumentException(STR."no entry found in \{c.getClass()}");
    try {
      var method = entry.get();
      method.setAccessible(true); // for anonymous class
      var handle = MethodHandles.lookup().unreflect(method);
      var param = handle.type().parameterList();
      if (param.size() < 2)
        throw new IllegalArgumentException("entry method must at least have 1 parameters (the `REPL` instance)");
      if (param.get(1) != replClass)
        throw new IllegalArgumentException("entry method must have `Repl` as first parameter");
      if (param.size() == 2) return new CommandGen(c, handle, null);
      // TODO: support more than 1 parameter
      var factory = argFactory.getOption(param.get(2));
      if (factory.isEmpty()) throw new IllegalArgumentException(STR."no argument factory found for \{param.get(2)}");
      return new CommandGen(c, handle, factory.get());
    } catch (IllegalAccessException e) {
      throw new IllegalArgumentException(STR."unable to unreflect entry for: \{c.names()}", e);
    }
  }

  public record Clue(
    @NotNull String name,
    @NotNull ImmutableSeq<CommandGen> command,
    @NotNull String argument
  ) {
    public Command.Result run(@NotNull Object repl) throws Throwable {
      return switch (command.size()) {
        case 1 -> command.getFirst().invoke(repl, argument);
        case 0 -> Command.Result.err(STR."Command `\{name}` not found", true);
        default -> Command.Result.err(command.view()
            .flatMap(s -> s.owner.names())
            .joinToString("`, `", "Ambiguous command name (`", "`), please be more accurate", s -> s),
          true);
      };
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
    return new Clue(name, cmd.filter(c -> c.owner.names()
      .anyMatch(n -> n.startsWith(name))), argument);
  }
}
