// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.util.NormalizeMode;
import org.aya.cli.repl.AbstractRepl;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class DefaultCommands {
  private DefaultCommands() {
  }

  public static ImmutableSeq<Command> defaultCommands() {
    return ImmutableSeq.of(
      QUIT,
      CHANGE_PROMPT,
      CHANGE_NORM_MODE,
      HelpCommand.INSTANCE,
      SHOW_TYPE,
      CHANGE_PP_WIDTH,
      TOGGLE_UNICODE
    );
  }

  public static final @NotNull Command CHANGE_PROMPT = new Command() {
    @Override public @NotNull ImmutableSeq<String> names() {
      return ImmutableSeq.of("prompt");
    }

    @Override public @NotNull String description() {
      return "Change the REPL prompt text";
    }

    @Override public @NotNull Command.Result execute(@NotNull String argument, @NotNull AbstractRepl repl) {
      repl.prompt = argument;
      return Result.successful("Changed prompt to `" + argument + "`", true);
    }
  };

  public static final @NotNull Command SHOW_TYPE = new Command() {
    @Override public @NotNull ImmutableSeq<String> names() {
      return ImmutableSeq.of("t", "type");
    }

    @Override public @NotNull String description() {
      return "Show the type of the given expression";
    }

    @Override public @NotNull Command.Result execute(@NotNull String argument, @NotNull AbstractRepl repl) {
      var type = repl.replCompiler.compileExprAndGetType(argument, repl.normalizeMode);
      return type != null ? Result.successful(repl.render(type), true) :
          Result.failed("Failed to get expression type", true);
    }
  };

  public static final @NotNull Command CHANGE_PP_WIDTH = new Command() {
    @Override public @NotNull ImmutableSeq<String> names() {
      return ImmutableSeq.of("print-width");
    }

    @Override public @NotNull String description() {
      return "Set printed output width";
    }

    @Override
    public @NotNull Command.Result execute(@NotNull String argument, @NotNull AbstractRepl repl) {
      var prettyPrintWidth = Integer.parseInt(argument.trim());
      repl.prettyPrintWidth = prettyPrintWidth;
      return Result.successful("Printed output width set to " + prettyPrintWidth, true);
    }
  };

  public static final @NotNull Command QUIT = new Command() {
    @Override public @NotNull ImmutableSeq<String> names() {
      return ImmutableSeq.of("quit", "exit", "q");
    }

    @Override public @NotNull String description() {
      return "Quit the REPL";
    }

    @Override
    public @NotNull Command.Result execute(@NotNull String argument, @NotNull AbstractRepl repl) {
      return Result.successful("See you space cow woof woof :3", false);
    }
  };

  public static final @NotNull Command CHANGE_NORM_MODE = new Command() {
    @Override public @NotNull ImmutableSeq<String> names() {
      return ImmutableSeq.of("normalize");
    }

    @Override public @NotNull String description() {
      return "Set the normalize mode (candidates: " + Arrays.toString(NormalizeMode.values()) + ")";
    }

    @Override
    public @NotNull Command.Result execute(@NotNull String argument, @NotNull AbstractRepl repl) {
      var normalizeMode = NormalizeMode.valueOf(argument.trim());
      repl.normalizeMode = normalizeMode;
      return Result.successful("Normalize mode set to " + normalizeMode, true);
    }
  };

  public static final @NotNull Command TOGGLE_UNICODE = new Command() {
    @Override public @NotNull ImmutableSeq<String> names() {
      return ImmutableSeq.of("unicode");
    }

    @Override public @NotNull String description() {
      return "Enable or disable unicode in REPL output";
    }

    @Override public @NotNull Command.Result execute(@NotNull String argument, @NotNull AbstractRepl repl) {
      var trim = argument.trim();
      boolean enableUnicode = trim.isEmpty() ? !repl.enableUnicode
          : Boolean.parseBoolean(trim);
      repl.enableUnicode = enableUnicode;
      return Result.successful("Unicode " + (enableUnicode ? "enabled" : "disabled"), true);
    }
  };
}
