// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import kala.control.Try;
import org.aya.cli.repl.jline.completer.AyaCompleters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jline.reader.Completer;

import java.util.function.Function;

public interface CommandArg {
  @NotNull Class<?> type();
  @NotNull Object parse(@NotNull String input) throws IllegalArgumentException;
  @Nullable Completer completer();
  boolean shellLike();

  record CommandArgImpl<R>(@NotNull Class<? extends R> type, @Nullable Completer completer, boolean shellLike,
                           @NotNull Function<String, R> f) implements CommandArg {
    @Override public @NotNull Object parse(@NotNull String input) throws IllegalArgumentException {
      return f.apply(input);
    }
  }

  private static <R> CommandArg from(@NotNull Class<? extends R> type, boolean shellLike, @Nullable Completer completer, @NotNull Function<String, R> f) {
    return new CommandArgImpl<>(type, completer, shellLike, f);
  }

  static <R> CommandArg from(@NotNull Class<? extends R> type, @Nullable Completer completer, @NotNull Function<String, R> f) {
    return from(type, false, completer, f);
  }

  static <R> CommandArg shellLike(@NotNull Class<? extends R> type, @Nullable Completer completer, @NotNull Function<String, R> f) {
    return from(type, true, completer, f);
  }

  static <T extends Enum<T>> CommandArg fromEnum(@NotNull Class<T> enumClass) {
    return from(enumClass, false, new AyaCompleters.EnumCompleter<>(enumClass), input -> Enum.valueOf(enumClass, input));
  }

  @NotNull CommandArg STRING = from(String.class, null, Function.identity());
  @NotNull CommandArg STRICT_INT = from(Integer.class, null, input ->
    Try.of(() -> Integer.parseInt(input)).getOrThrow(IllegalArgumentException::new));
  @NotNull CommandArg STRICT_BOOLEAN = from(Boolean.class, AyaCompleters.BOOL, s -> {
    if (s.equalsIgnoreCase("true")) return true;
    if (s.equalsIgnoreCase("false")) return false;
    throw new IllegalArgumentException("not an boolean value");
  });
}
