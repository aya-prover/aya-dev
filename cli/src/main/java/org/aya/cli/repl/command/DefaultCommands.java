// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import kala.collection.ArraySeq;
import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.api.util.NormalizeMode;
import org.aya.cli.repl.Repl;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.Candidate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public interface DefaultCommands {
  static @NotNull CommandManager defaultCommandManager() {
    return new CommandManager(ImmutableSeq.of(
      QUIT,
      CHANGE_PROMPT,
      CHANGE_NORM_MODE,
      SHOW_TYPE,
      CHANGE_PP_WIDTH,
      TOGGLE_UNICODE,
      CHANGE_CWD,
      PRINT_CWD,
      LOAD_FILE
    ));
  }

  @NotNull Command CHANGE_PROMPT = new Command(ImmutableSeq.of("prompt"), "Change the REPL prompt text") {
    @Entry public @NotNull Command.Result execute(@NotNull String argument, @NotNull Repl repl) {
      repl.config.prompt = argument;
      return Result.ok("Changed prompt to `" + argument + "`", true);
    }
  };

  @NotNull Command SHOW_TYPE = new Command.CodeCommand(ImmutableSeq.of("t", "type"), "Show the type of the given expression") {
    @Entry public @NotNull Command.Result execute(@NotNull String argument, @NotNull Repl repl) {
      var type = repl.replCompiler.compileExpr(argument, repl.config.normalizeMode);
      return type != null ? new Result(Output.stdout(repl.render(type)), true)
        : Result.err("Failed to get expression type", true);
    }
  };

  @NotNull Command LOAD_FILE = new Command.FileCommand(ImmutableSeq.of("l", "load"), "Load file into REPL") {
    @Entry public @NotNull Command.Result execute(@NotNull String argument, @NotNull Repl repl) {
      try {
        repl.replCompiler.loadToContext(Path.of(argument));
      } catch (IOException e) {
        return Result.err("Unable to read file: " + e.getLocalizedMessage(), true);
      }
      // SingleFileCompiler would print result to REPL.
      return new Result(Output.empty(), true);
    }
  };

  @NotNull Command CHANGE_CWD = new Command.FileCommand(ImmutableSeq.of("cd"), "Change current working directory") {
    @Entry public @NotNull Command.Result execute(@NotNull String argument, @NotNull Repl repl) {
      var path = repl.resolveFile(argument);
      if (!Files.isDirectory(path)) return Result.err("cd: no such file or directory: " + argument, true);
      repl.cwd = path;
      // for jline completer to work properly, but it does not have any effect actually
      System.setProperty("user.dir", path.toAbsolutePath().toString());
      return new Result(Output.empty(), true);
    }
  };

  @NotNull Command PRINT_CWD = new Command(ImmutableSeq.of("pwd"), "Print current working directory") {
    @Entry public @NotNull Command.Result execute(@NotNull String argument, @NotNull Repl repl) {
      return new Result(Output.stdout(repl.cwd.toAbsolutePath().toString()), true);
    }
  };

  @NotNull Command CHANGE_PP_WIDTH = new Command(ImmutableSeq.of("print-width"), "Set printed output width") {
    @Entry public @NotNull Command.Result execute(@NotNull String argument, @NotNull Repl repl) {
      var prettyPrintWidth = Integer.parseInt(argument.trim());
      repl.prettyPrintWidth = prettyPrintWidth;
      return Result.ok("Printed output width set to " + prettyPrintWidth, true);
    }
  };

  @NotNull Command QUIT = new Command(ImmutableSeq.of("quit", "exit", "q"), "Quit the REPL") {
    @Entry public @NotNull Command.Result execute(@NotNull String argument, @NotNull Repl repl) {
      return Result.ok("See you space cow woof woof :3", false);
    }
  };

  @NotNull Command CHANGE_NORM_MODE = new Command.StringCommand(ImmutableSeq.of("normalize"), "Set or display the normalization mode (candidates: " + Arrays.toString(NormalizeMode.values()) + ")") {
    public SeqView<Candidate> params() {
      return ArraySeq.of(NormalizeMode.values()).view().map(Enum::name).map(Candidate::new);
    }

    @Entry public @NotNull Command.Result execute(@NotNull String argument, @NotNull Repl repl) {
      if (argument.isBlank()) return Result.ok("Normalization mode: " + repl.config.normalizeMode, true);
      else try {
        var normalizeMode = NormalizeMode.valueOf(argument);
        repl.config.normalizeMode = normalizeMode;
        return Result.ok("Normalization mode set to " + normalizeMode, true);
      } catch (IllegalArgumentException ignored) {
        return Result.err("Unknown normalization mode " + argument, true);
      }
    }
  };

  @NotNull Command TOGGLE_UNICODE = new Command.StringCommand(ImmutableSeq.of("unicode"), "Enable or disable unicode in REPL output") {
    public SeqView<Candidate> params() {
      return Seq.of(true, false).view().map(b -> new Candidate(b.toString()));
    }

    @Entry public @NotNull Command.Result execute(@NotNull String argument, @NotNull Repl repl) {
      var trim = argument.trim();
      boolean enableUnicode = trim.isEmpty() ? !repl.config.enableUnicode
        : Boolean.parseBoolean(trim);
      repl.config.enableUnicode = enableUnicode;
      return Result.ok("Unicode " + (enableUnicode ? "enabled" : "disabled"), true);
    }
  };
}
