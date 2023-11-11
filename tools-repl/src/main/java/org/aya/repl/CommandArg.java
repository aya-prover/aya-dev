// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.repl;

import kala.collection.Seq;
import kala.collection.mutable.MutableList;
import kala.control.Either;
import kala.control.Try;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jline.reader.Completer;
import org.jline.reader.impl.completer.AggregateCompleter;

import java.util.function.Function;

public interface CommandArg {
  @NotNull Class<?> type();
  @NotNull Object parse(@NotNull String input) throws IllegalArgumentException;
  @Nullable Completer completer();
  /**
   * Affects the repl parser behavior of the argument.
   *
   * @return true to parse the argument with the default parser,
   * otherwise to parse with the antlr-based parser.
   * @see ReplParser
   */
  boolean shellLike();

  record CommandArgImpl<R>(
    @Override @NotNull Class<? extends R> type,
    @Override @Nullable Completer completer,
    @Override boolean shellLike,
    @NotNull Function<String, R> f
  ) implements CommandArg {
    @Override public @NotNull R parse(@NotNull String input) throws IllegalArgumentException {
      return f.apply(input);
    }
  }

  private static <R> CommandArgImpl<R> from(@NotNull Class<? extends R> type, boolean shellLike, @Nullable Completer completer, @NotNull Function<String, R> f) {
    return new CommandArgImpl<>(type, completer, shellLike, f);
  }

  static <R> CommandArgImpl<R> from(@NotNull Class<? extends R> type, @Nullable Completer completer, @NotNull Function<String, R> f) {
    return from(type, false, completer, f);
  }

  static <R> CommandArgImpl<R> shellLike(@NotNull Class<? extends R> type, @Nullable Completer completer, @NotNull Function<String, R> f) {
    return from(type, true, completer, f);
  }

  static <T extends Enum<T>> CommandArgImpl<T> fromEnum(@NotNull Class<T> enumClass) {
    return from(enumClass, false, new ReplCompleters.EnumCompleter<>(enumClass),
      input -> chooseEnum(enumClass, input));
  }

  static <L, R, T extends ArgEither<L, R>> CommandArgImpl<T> fromEither(
    @NotNull Class<T> type,
    @NotNull CommandArgImpl<L> leftArg,
    @NotNull CommandArgImpl<R> rightArg,
    @NotNull Function<L, T> createLeft,
    @NotNull Function<R, T> createRight
  ) {
    var completer = MutableList.<Completer>create();
    if (leftArg.completer() != null) completer.append(leftArg.completer());
    if (rightArg.completer() != null) completer.append(rightArg.completer());
    return from(type, leftArg.shellLike || rightArg.shellLike,
      new AggregateCompleter(completer.asJava()),
      input -> {
        if (input.trim().isEmpty()) throw new IllegalArgumentException("Empty input");
        try {
          return createLeft.apply(leftArg.parse(input));
        } catch (IllegalArgumentException ignored) {
          return createRight.apply(rightArg.parse(input));
        }
      });
  }

  private static <T extends Enum<T>> @NotNull T chooseEnum(@NotNull Class<T> enumClass, @NotNull String name) {
    var trimName = name.trim();
    if (trimName.isEmpty()) throw new IllegalArgumentException("Empty enum value");
    try {
      return Enum.valueOf(enumClass, trimName);
    } catch (IllegalArgumentException ignored) {
      var one = Seq.of(enumClass.getEnumConstants())
        .findFirst(n -> n.name().toLowerCase().startsWith(trimName.toLowerCase()));
      if (one.isEmpty()) throw new IllegalArgumentException("No such enum constant: " + name);
      return one.get();
    }
  }

  interface ArgEither<E, T> {
    @NotNull Either<E, T> value();
  }

  @NotNull CommandArg STRING = from(String.class, null, Function.identity());
  @NotNull CommandArg STRICT_INT = from(Integer.class, null, input ->
    Try.of(() -> Integer.parseInt(input)).getOrThrow(IllegalArgumentException::new));
  @NotNull CommandArg STRICT_BOOLEAN = from(Boolean.class, ReplCompleters.BOOL, s -> {
    if (s.equalsIgnoreCase("true")) return true;
    if (s.equalsIgnoreCase("false")) return false;
    if (s.equalsIgnoreCase("yes")) return true;
    if (s.equalsIgnoreCase("no")) return false;
    throw new IllegalArgumentException("not an boolean value");
  });
}
