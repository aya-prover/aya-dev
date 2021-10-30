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

  static <R> CommandArg from(@NotNull Class<? extends R> type, @Nullable Completer completer, @NotNull Function<String, R> f) {
    return new CommandArg() {
      @NotNull @Override public Class<?> type() {
        return type;
      }

      @Override public @NotNull Object parse(@NotNull String input) throws IllegalArgumentException {
        return f.apply(input);
      }

      @Override public @Nullable Completer completer() {
        return completer;
      }
    };
  }

  static <T extends Enum<T>> CommandArg fromEnum(@NotNull Class<T> enumClass) {
    return from(enumClass, new AyaCompleters.EnumCompleter<>(enumClass), input -> Enum.valueOf(enumClass, input));
  }

  @NotNull CommandArg STRING = from(String.class, null, Function.identity());
  @NotNull CommandArg STRICT_INT = from(Integer.class, null, input ->
    Try.of(() -> Integer.parseInt(input)).getOrThrow(IllegalArgumentException::new));
  @NotNull CommandArg STRICT_BOOLEAN = from(Boolean.class, AyaCompleters.BooleanCompleter.INSTANCE, s -> {
    if (s.equals("true")) return true;
    if (s.equals("false")) return false;
    throw new IllegalArgumentException("not an boolean value");
  });
}
