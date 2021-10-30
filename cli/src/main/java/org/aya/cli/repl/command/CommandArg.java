// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public interface CommandArg {
  @NotNull Class<?> type();
  @NotNull Object parse(@NotNull String input) throws IllegalArgumentException;

  static <R> CommandArg from(@NotNull Class<? extends R> type, @NotNull Function<String, R> f) {
    return new CommandArg() {
      @NotNull @Override public Class<?> type() {
        return type;
      }

      @Override public @NotNull Object parse(@NotNull String input) throws IllegalArgumentException {
        return f.apply(input);
      }
    };
  }

  static <T extends Enum<T>> CommandArg fromEnum(@NotNull Class<T> enumClass) {
    return new CommandArg() {
      @NotNull @Override public Class<?> type() {
        return enumClass;
      }

      @Override public @NotNull Object parse(@NotNull String input) throws IllegalArgumentException {
        return Enum.valueOf(enumClass, input);
      }
    };
  }

  @NotNull CommandArg STRING = from(String.class, Function.identity());
  @NotNull CommandArg STRICT_BOOLEAN = from(Boolean.class, s -> {
    if (s.equals("true")) return true;
    if (s.equals("false")) return false;
    throw new IllegalArgumentException("not an boolean value");
  });
}
